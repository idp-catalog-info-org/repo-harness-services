/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.beans.FeatureName.CI_USE_BUILDX_ON_K8;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.AISHWARYA_LAD;
import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.EOIN_MCAFEE;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SAHITHI;
import static io.harness.rule.OwnerRule.SATYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GARStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.config.Operation;
import io.harness.ci.config.PluginField;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.config.VmContainerlessStepConfig;
import io.harness.ci.config.VmImageConfig;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionConfigRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
public class CIExecutionConfigServiceTest extends CIExecutionTestBase {
  @Mock CIExecutionConfigRepository cIExecutionConfigRepository;
  @Inject CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  @Mock private CIExecutionServiceConfig ciExecutionServiceConfigMock;
  @Mock private PluginSettingUtils pluginSettingUtils;
  @InjectMocks private CIExecutionConfigServiceImpl ciExecutionConfigServiceWithMocks;
  @Mock CIFeatureFlagService featureFlagService;

  @Before
  public void setUp() {
    on(ciExecutionConfigService).set("configRepository", cIExecutionConfigRepository);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getAddonImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1,4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getAddonImage("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImage("acct1");
    assertThat(customAddonImage).isEqualTo("addon:1.3.4");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImage());
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getAddonImageRootlessTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .addOnImageRootless("harness/ci-addon:rootless-1.4.0")
                                            .liteEngineImageRootless("harness/ci-lite-engine:rootless-1.4.0")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getAddonImageRootless("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImageRootless("acct");
    assertThat(customAddonImage).isEqualTo("harness/ci-addon:rootless-1.4.0");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImageRootless());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLEImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1.4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getLiteEngineImage("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImage("acct1");
    assertThat(customAddonImage).isEqualTo("le:1.4.4");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImage());
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void getCacheProxyImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1.4.4")
                                            .cacheProxyImage("cacheProxy:1.4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customCacheProxyImage = ciExecutionConfigService.getCacheProxyImage("acct");
    String defaultCacheProxyImage = ciExecutionConfigService.getCacheProxyImage("acct1");
    assertThat(customCacheProxyImage).isEqualTo("cacheProxy:1.4.4");
    assertThat(defaultCacheProxyImage)
        .isEqualTo(ciExecutionServiceConfig.getStepConfig().getCacheProxyConfig().getImage());
  }
  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getLiteEngineImageRootlessTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .addOnImageRootless("harness/ci-addon:rootless-1.4.0")
                                            .liteEngineImageRootless("harness/ci-lite-engine:rootless-1.4.0")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getLiteEngineImageRootless("acct");
    String defaultAddonImage = ciExecutionConfigService.getLiteEngineImageRootless("acct");
    assertThat(customAddonImage).isEqualTo("harness/ci-lite-engine:rootless-1.4.0");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getLiteEngineImageRootless());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getDeprecatedImages_NoDeprecatedImagesTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.2.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("ArtifactoryUploadImage").version("art:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheGCSImage").version("cachegcs:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("S3UploadImage").version("s3upload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheAzureImage").version("cacheazure:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("GCSUploadImage").version("gcsUpload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("SecurityImage").version("gcsUpload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushDockerImage").version("bpdr:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("GitCloneImage").version("gc:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushECRConfigImage").version("bpecr:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushGCRConfigImage").version("bpgcr:1.2.3").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(Arrays.asList());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_LEIsDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.0.1")
                                            .liteEngineImage("harness/ci-lite-engine:1.0.1")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.0.1").build(),
            DeprecatedImageInfo.builder().tag("LiteEngineImage").version("harness/ci-lite-engine:1.0.1").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_LEIsNotDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.2.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos = Arrays.asList();
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_AddonIsDeprecatedLENotDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.1.0").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getPluginVersionTestCustomConfig_ShouldPass() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .iacmTerraform("iacmTF:123")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, "acct").getImage())
        .isEqualTo("gc:abc");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, "acct").getImage())
        .isEqualTo("bpdr:1.2.4");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, "acct").getImage())
        .isEqualTo("bpgcr:1.2.3");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TERRAFORM_PLUGIN, "acct").getImage())
        .isEqualTo("iacmTF:123");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getDeprecatedImagesTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    CIExecutionImages deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getBuildAndPushECRTag()).isNull();
    assertThat(deprecatedImages.getGcsUploadTag()).isNull();
    assertThat(deprecatedImages.getLiteEngineTag()).isNull();
    assertThat(deprecatedImages.getAddonTag()).isNull();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getDeprecatedImagesWithDeprecatedTagsTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:0.2.4")
                                            .addOnImage("harness/ci-addon:0.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.1.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:0.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:0.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:0.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    CIExecutionImages deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getBuildAndPushECRTag()).isNull();
    assertThat(deprecatedImages.getGcsUploadTag()).isNull();
    assertThat(deprecatedImages.getLiteEngineTag()).isNull();
    assertThat(deprecatedImages.getAddonTag()).isEqualTo("harness/ci-addon:0.1.0");
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void getDeprecatedImagesWithDeprecatedTagsWithImproperVersionsTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:0.2.4")
                                            .addOnImage("harness/ci-addon:0.1.0@123")
                                            .liteEngineImage("harness/ci-lite-engine:0.1.0.2-test-build")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:0.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:0.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:0.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    CIExecutionImages deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getAddonTag()).isEqualTo("harness/ci-addon:0.1.0@123");
    assertThat(deprecatedImages.getLiteEngineTag()).isEqualTo("harness/ci-lite-engine:0.1.0.2-test-build");

    executionConfig.setAddOnImage("harness/ci-addon:0.1.0");
    deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getAddonTag()).isEqualTo("harness/ci-addon:0.1.0");

    executionConfig.setAddOnImage("harness/ci-addon");
    deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getAddonTag()).isNull();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getDeprecatedTags_ShouldSkipSha256DigestReferencesTest() {
    CIExecutionConfig executionConfig =
        CIExecutionConfig.builder()
            .accountIdentifier("acct")
            .addOnImage("harness/ci-addon@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
            .liteEngineImage(
                "harness/ci-lite-engine@sha256:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(Arrays.asList());
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getDeprecatedTags_ShouldSkipSha512DigestReferencesTest() {
    CIExecutionConfig executionConfig =
        CIExecutionConfig.builder()
            .accountIdentifier("acct")
            .addOnImage("harness/ci-addon@sha512:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
                + "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
            .liteEngineImage("harness/ci-lite-engine@sha384:1234567890abcdef1234567890abcdef"
                + "1234567890abcdef1234567890abcdef1234567890abcdef")
            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(Arrays.asList());
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getDeprecatedImages_ShouldSkipDigestReferencesTest() {
    CIExecutionConfig executionConfig =
        CIExecutionConfig.builder()
            .accountIdentifier("acct")
            .addOnImage("harness/ci-addon@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
            .liteEngineImage(
                "harness/ci-lite-engine@sha512:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
                + "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    CIExecutionImages deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getAddonTag()).isNull();
    assertThat(deprecatedImages.getLiteEngineTag()).isNull();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getDefaultTest() {
    CIExecutionImages ciExecutionImages = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.VM);
    assertThat(ciExecutionImages.getAddonTag()).isNull();
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("vm-gitClone");
    assertThat(ciExecutionImages.getArtifactoryUploadTag()).isEqualTo("vm-artifactoryUpload");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("vm-cacheGCS");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("vm-security");
    assertThat(ciExecutionImages.getCacheProxyImage()).isEqualTo("vm-cacheProxy");

    ciExecutionImages = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.K8);
    assertThat(ciExecutionImages.getAddonTag()).isEqualTo("harness/ci-addon:1.4.0");
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("gc:1.2.3");
    assertThat(ciExecutionImages.getArtifactoryUploadTag()).isEqualTo("art:1.2.3");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("cachegcs:1.2.3");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("sc:1.2.3");
    assertThat(ciExecutionImages.getCacheProxyImage()).isEqualTo("harness/harness-cache-server:1.7.8");
    assertThat(ciExecutionImages.getAddonTagRootless()).isEqualTo("harness/ci-addon:rootless-1.4.0");
    assertThat(ciExecutionImages.getLiteEngineTagRootless()).isEqualTo("harness/ci-lite-engine:rootless-1.4.0");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getPluginVersionVMTestCustomConfig_ShouldPass() {
    VmImageConfig vmImageConfig =
        VmImageConfig.builder().gitClone("vm_git_clone").buildAndPushDockerRegistry("docker").build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, "acct"))
        .isEqualTo("vm_git_clone");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, "acct")).isEqualTo("docker");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, "acct"))
        .isEqualTo("vm-buildAndPushECR");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, "acct"))
        .isEqualTo("vm-buildAndPushGCR");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, "acct"))
        .isEqualTo("vm-buildAndPushGAR");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, "acct"))
        .isEqualTo("vm-cacheS3");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_AZURE, "acct"))
        .isEqualTo("vm-cacheAzure");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE, "acct"))
        .isEqualTo("vm-cache");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, "acct"))
        .isEqualTo("vm-cacheGCS");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_AZURE, "acct"))
        .isEqualTo("vm-cacheAzure");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, "acct"))
        .isEqualTo("vm-cacheS3");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE, "acct")).isEqualTo("vm-cache");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, "acct"))
        .isEqualTo("vm-artifactoryUpload");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, "acct"))
        .isEqualTo("vm-security");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getCustomerConfigTest() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .gitClone("vm_git_clone")
                                      .cacheProxy("vm_cache_proxy")
                                      .buildAndPushDockerRegistry("docker")
                                      .build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .addOnImageRootless("addonRootless")
                                            .liteEngineImageRootless("liteEngineImageRootless")
                                            .cacheProxyImage("cacheProxy")
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    // For VM overrides only
    CIExecutionImages ciExecutionImages =
        ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.VM, true);

    assertThat(ciExecutionImages.getAddonTag()).isNull();
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("vm_git_clone");
    assertThat(ciExecutionImages.getCacheProxyImage()).isEqualTo("vm_cache_proxy");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("docker");
    assertThat(ciExecutionImages.getCacheGCSTag()).isNull();
    assertThat(ciExecutionImages.getSecurityTag()).isNull();

    // For K8 overrides only
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.K8, true);

    assertThat(ciExecutionImages.getAddonTag()).isEqualTo("addon");
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("gc:abc");
    assertThat(ciExecutionImages.getCacheProxyImage()).isEqualTo("cacheProxy");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("bpdr:1.2.4");
    assertThat(ciExecutionImages.getCacheGCSTag()).isNull();
    assertThat(ciExecutionImages.getSecurityTag()).isNull();
    assertThat(ciExecutionImages.getAddonTagRootless()).isEqualTo("addonRootless");
    assertThat(ciExecutionImages.getLiteEngineTagRootless()).isEqualTo("liteEngineImageRootless");

    // For VM whole config
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.VM, false);

    assertThat(ciExecutionImages.getAddonTag()).isNull();
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("vm_git_clone");
    assertThat(ciExecutionImages.getCacheProxyImage()).isEqualTo("vm_cache_proxy");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("docker");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("vm-cacheGCS");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("vm-security");
    assertThat(ciExecutionImages.getCacheS3Tag()).isEqualTo("vm-cacheS3");
    assertThat(ciExecutionImages.getCacheAzureTag()).isEqualTo("vm-cacheAzure");
    assertThat(ciExecutionImages.getCacheTag()).isEqualTo("vm-cache");

    // For K8 whole config
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.K8, false);

    assertThat(ciExecutionImages.getAddonTag()).isEqualTo("addon");
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("gc:abc");
    assertThat(ciExecutionImages.getCacheProxyImage()).isEqualTo("cacheProxy");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("bpdr:1.2.4");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("cachegcs:1.2.3");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("sc:1.2.3");
    assertThat(ciExecutionImages.getSscaOrchestrationTag()).isEqualTo("sscaorchestrate:0.0.1");
    assertThat(ciExecutionImages.getSscaEnforcementTag()).isEqualTo("sscaEnforcement:0.0.1");
    assertThat(ciExecutionImages.getSlsaVerificationTag()).isEqualTo("slsaVerification:0.0.1");
    assertThat(ciExecutionImages.getSscaComplianceTag()).isEqualTo("sscaCompliance:0.0.1");
    assertThat(ciExecutionImages.getSscaArtifactSigningTag()).isEqualTo("sscaArtifactSigning:0.0.1");
    assertThat(ciExecutionImages.getSscaArtifactVerificationTag()).isEqualTo("sscaArtifactVerification:0.0.1");
    assertThat(ciExecutionImages.getProvenanceTag()).isEqualTo("provenance:0.0.1");
    assertThat(ciExecutionImages.getSscaCdxgenOrchestrationTag()).isEqualTo("sscaCdxgenOrchestration:0.0.1");
    assertThat(ciExecutionImages.getAddonTagRootless()).isEqualTo("addonRootless");
    assertThat(ciExecutionImages.getLiteEngineTagRootless()).isEqualTo("liteEngineImageRootless");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void updateTest() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .gitClone("vm_git_clone")
                                      .buildAndPushDockerRegistry("docker")
                                      .cacheProxy("vm-cacheProxy")
                                      .build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .cacheProxyImage("cacheProxy")
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .addOnImageRootless("addonRootless")
                                            .liteEngineImageRootless("liteEngineImageRootless")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();

    Operation operation1 = new Operation();
    operation1.setField(PluginField.BUILD_PUSH_ECR.getLabel());
    operation1.setValue("ecr_vm");

    Operation operation2 = new Operation();
    operation2.setField(PluginField.GIT_CLONE.getLabel());
    operation2.setValue("vm_git_clone_changed");

    Operation operation3 = new Operation();
    operation3.setField(PluginField.CACHE_PROXY.getLabel());
    operation3.setValue("vm_cache_proxy_changed");

    operations.add(operation1);
    operations.add(operation2);
    operations.add(operation3);
    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.VM);
    assertThat(executionConfig.getVmImageConfig().getGitClone()).isEqualTo("vm_git_clone_changed");
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushECR()).isEqualTo("ecr_vm");
    assertThat(executionConfig.getVmImageConfig().getCacheProxy()).isEqualTo("vm_cache_proxy_changed");
    assertThat(executionConfig.getGitCloneImage()).isEqualTo("gc:abc");
    assertThat(executionConfig.getCacheProxyImage()).isEqualTo("cacheProxy");

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.K8);
    assertThat(executionConfig.getGitCloneImage()).isEqualTo("vm_git_clone_changed");
    assertThat(executionConfig.getBuildAndPushECRImage()).isEqualTo("ecr_vm");
    assertThat(executionConfig.getCacheProxyImage()).isEqualTo("vm_cache_proxy_changed");
    assertThat(executionConfig.getAddOnImageRootless()).isEqualTo("addonRootless");
    assertThat(executionConfig.getLiteEngineImageRootless()).isEqualTo("liteEngineImageRootless");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getPluginVersionTestDefaultConfig_ShouldPass() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgcr:1.2.3")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .cacheAzureTag("cacheazure:1.2.3")
                                            .iacmOpenTofu("tofu:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, "acct").getImage())
        .isEqualTo("gc:1.2.3");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, "acct").getImage())
        .isEqualTo("bpdr:1.2.3");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, "acct").getImage())
        .isEqualTo("harness_terraform:dev");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMDockerBuildx() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxConfig(ContainerlessPluginConfig.builder().name("dockerBuildxConfig").build())
                    .build())
            .build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                        .caching(ParameterField.createValueField(true))
                                        .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(dockerStepInfo)).thenReturn(true);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo)
            .getName();
    assertThat(pluginName).isNotEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMEcrBuildx() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxEcrConfig").build())
                    .build())
            .build();
    ECRStepInfo ecrStepInfo = ECRStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(ecrStepInfo)).thenReturn(true);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.ECR, ecrStepInfo).getName();
    assertThat(pluginName).isNotEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMDocker() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxConfig(ContainerlessPluginConfig.builder().name("dockerBuildxConfig").build())
                    .build())
            .build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                        .caching(ParameterField.createValueField(true))
                                        .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(dockerStepInfo)).thenReturn(false);
    ContainerlessPluginConfig pluginConfig =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo);
    assertThat(pluginConfig).isNull();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMEcr() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxEcrConfig").build())
                    .build())
            .build();
    ECRStepInfo ecrStepInfo = ECRStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(ecrStepInfo)).thenReturn(false);
    ContainerlessPluginConfig pluginConfig =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.ECR, ecrStepInfo);
    assertThat(pluginConfig).isNull();
  }

  @Test
  @Owner(developers = EOIN_MCAFEE)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMGAR() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxGarConfig").build())
                    .build())
            .build();
    GARStepInfo garStepInfo = GARStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(garStepInfo)).thenReturn(false);
    ContainerlessPluginConfig pluginConfig =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.GAR, garStepInfo);
    assertThat(pluginConfig).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_sscaPluginsWithGlobalAccountId() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .sscaOrchestrationTag("sscaOrchestrationTag")
                                            .sscaEnforcementTag("sscaEnforcementTag")
                                            .sscaArtifactSigningTag("sscaArtifactSigningTag")
                                            .sscaArtifactVerificationTag("sscaArtifactVerificationTag")
                                            .sscaCdxgenOrchestrationTag("sscaCdxgenOrchestrationTag")
                                            .provenanceTag("provenanceTag")
                                            .slsaVerificationTag("slsaVerificationTag")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("__GLOBAL_ACCOUNT_ID__"))
        .thenReturn(Optional.of(executionConfig));
    StepImageConfig actualSscaOrchestrationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, "acct");
    assertThat(actualSscaOrchestrationExecutionConfig).isNotNull();
    assertThat(actualSscaOrchestrationExecutionConfig.getImage()).isEqualTo("sscaOrchestrationTag");

    StepImageConfig actualSscaEnforcementExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, "acct");
    assertThat(actualSscaEnforcementExecutionConfig).isNotNull();
    assertThat(actualSscaEnforcementExecutionConfig.getImage()).isEqualTo("sscaEnforcementTag");

    StepImageConfig actualSscaCdxgenOrchestrationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, "acct");
    assertThat(actualSscaCdxgenOrchestrationExecutionConfig).isNotNull();
    assertThat(actualSscaCdxgenOrchestrationExecutionConfig.getImage()).isEqualTo("sscaCdxgenOrchestrationTag");

    StepImageConfig actualProvenanceExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, "acct");
    assertThat(actualProvenanceExecutionConfig).isNotNull();
    assertThat(actualProvenanceExecutionConfig.getImage()).isEqualTo("provenanceTag");

    StepImageConfig actualSalsaVerificationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, "acct");
    assertThat(actualSalsaVerificationExecutionConfig).isNotNull();
    assertThat(actualSalsaVerificationExecutionConfig.getImage()).isEqualTo("slsaVerificationTag");

    StepImageConfig actualSscaArtifactSigningExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_SIGNING, "acct");
    assertThat(actualSscaArtifactSigningExecutionConfig).isNotNull();
    assertThat(actualSscaArtifactSigningExecutionConfig.getImage()).isEqualTo("sscaArtifactSigningTag");

    StepImageConfig actualSscaArtifactVerificationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, "acct");
    assertThat(actualSscaArtifactVerificationExecutionConfig).isNotNull();
    assertThat(actualSscaArtifactVerificationExecutionConfig.getImage()).isEqualTo("sscaArtifactVerificationTag");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_sscaPluginsWithGlobalAccountId() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .sscaOrchestration("sscaOrchestrationTag")
                                      .sscaEnforcement("sscaEnforcementTag")
                                      .sscaCdxgenOrchestration("sscaCdxgenOrchestrationTag")
                                      .slsaVerification("slsaVerificationTag")
                                      .sscaArtifactSigning("sscaArtifactSigningTag")
                                      .sscaArtifactVerification("sscaArtifactVerificationTag")
                                      .build();
    CIExecutionConfig executionConfig =
        CIExecutionConfig.builder().accountIdentifier("acct").vmImageConfig(vmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("__GLOBAL_ACCOUNT_ID__"))
        .thenReturn(Optional.of(executionConfig));
    String actualSscaOrchestrationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, "acct");
    assertThat(actualSscaOrchestrationExecutionConfig).isNotNull();
    assertThat(actualSscaOrchestrationExecutionConfig).isEqualTo("sscaOrchestrationTag");

    String actualSscaEnforcementExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, "acct");
    assertThat(actualSscaEnforcementExecutionConfig).isNotNull();
    assertThat(actualSscaEnforcementExecutionConfig).isEqualTo("sscaEnforcementTag");

    String actualSscaCdxgenOrchestrationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, "acct");
    assertThat(actualSscaCdxgenOrchestrationExecutionConfig).isNotNull();
    assertThat(actualSscaCdxgenOrchestrationExecutionConfig).isEqualTo("sscaCdxgenOrchestrationTag");

    String actualSscaArtifactSigningExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_SIGNING, "acct");
    assertThat(actualSscaArtifactSigningExecutionConfig).isNotNull();
    assertThat(actualSscaArtifactSigningExecutionConfig).isEqualTo("sscaArtifactSigningTag");

    String actualSscaArtifactVerificationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, "acct");
    assertThat(actualSscaArtifactVerificationExecutionConfig).isNotNull();
    assertThat(actualSscaArtifactVerificationExecutionConfig).isEqualTo("sscaArtifactVerificationTag");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_noConfigsExist() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    CIStepConfig defaultStepConfig = getDefaultStepeConfig();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaOrchestrationConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaCdxgenOrchestrationConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaArtifactSigningConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaArtifactVerificationConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaEnforcementConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getProvenanceConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSlsaVerificationConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getArtifactoryUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGitCloneConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushECRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushACRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGCRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGARConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getS3UploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushDockerRegistryConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSecurityConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGcsUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_CHECKOV, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmCheckovConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_SEC, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFSecConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_COMPLIANCE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFComplianceConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_LINT, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFLintConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTerraformConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmOpenTofuConfig().getImage());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_onlyGlobalConfigExists() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    CIStepConfig defaultStepConfig = getDefaultStepeConfig();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    CIExecutionConfig globalExecutionConfig = getCiExecutionConfig("global");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaOrchestrationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaEnforcementTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaCdxgenOrchestrationTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaArtifactSigningTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaArtifactVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getProvenanceTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSlsaVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getArtifactoryUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGitCloneConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushECRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushACRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGCRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGARConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getS3UploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushDockerRegistryConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSecurityConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGcsUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_CHECKOV, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmCheckovConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_SEC, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFSecConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_COMPLIANCE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFComplianceConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_LINT, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFLintConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTerraformConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmOpenTofuConfig().getImage());
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_onlyAccountLevelConfigExists() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    CIExecutionConfig accountLevelExecutionConfig = getCiExecutionConfig("accountLevel");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaOrchestrationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaEnforcementTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaCdxgenOrchestrationTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaArtifactSigningTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaArtifactVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getProvenanceTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSlsaVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getArtifactoryUploadTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGitCloneImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushECRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushACRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGCRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGARImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getS3UploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushDockerRegistryImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSecurityImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGcsUploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_CHECKOV, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmCheckov());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_SEC, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTFSec());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_COMPLIANCE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTFCompliance());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_LINT, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTFLint());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTerraform());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmOpenTofu());
  }

  @Test
  @Owner(developers = AISHWARYA_LAD)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_BuildxEnabled() {
    String accountId = "acc";
    CIExecutionConfig ciExecutionConfig = getCiExecutionConfig("default");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(ciExecutionConfig));
    CIStepInfoType stepInfoType = CIStepInfoType.DOCKER;
    doReturn(true).when(featureFlagService).isEnabled(eq(CI_USE_BUILDX_ON_K8), anyString());

    StepImageConfig result = ciExecutionConfigService.getPluginVersionForK8(stepInfoType, accountId);
    assertThat(result.getImage()).isNotEqualTo("defaultbuildAndPushDockerRegistry");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_BothAccountLevelAndGlobalConfigsExist() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    CIExecutionConfig accountLevelExecutionConfig = getCiExecutionConfig("accountLevel");
    CIExecutionConfig globalExecutionConfig = getCiExecutionConfig("global");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaOrchestrationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaEnforcementTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaCdxgenOrchestrationTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaArtifactSigningTag());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaArtifactVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getProvenanceTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSlsaVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getArtifactoryUploadTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGitCloneImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushECRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushACRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGCRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGARImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getS3UploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushDockerRegistryImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSecurityImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGcsUploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_CHECKOV, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmCheckov());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_SEC, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTFSec());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_COMPLIANCE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTFCompliance());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_LINT, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTFLint());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmTerraform());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getIacmOpenTofu());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_BothAccountLevelAndGlobalConfigsExist() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig accountLevelVmImageConfig = getVmImageConfig("accountLevel");
    VmImageConfig globalVmImageConfig = getVmImageConfig("global");
    CIExecutionConfig accountLevelExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(accountId).vmImageConfig(accountLevelVmImageConfig).build();
    CIExecutionConfig globalExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(globalAccountId).vmImageConfig(globalVmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaCdxgenOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaArtifactSigning());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaArtifactVerification());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getArtifactoryUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGitClone());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushECR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushACR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGCR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGAR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getS3Upload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushDockerRegistry());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSecurity());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGcsUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTerraform());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmOpenTofu());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TF_SEC, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTFSec());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TF_LINT, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTFLint());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TF_COMPLIANCE, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTFCompliance());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_CHECKOV, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmCheckov());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_onlyGlobalConfigExists() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig globalVmImageConfig = getVmImageConfig("global");
    CIStepConfig defaultStepConfig = getDefaultStepeConfig();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    CIExecutionConfig globalExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(globalAccountId).vmImageConfig(globalVmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(globalVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(globalVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId))
        .isEqualTo(globalVmImageConfig.getSscaCdxgenOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId))
        .isEqualTo(globalVmImageConfig.getSscaArtifactSigning());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId))
        .isEqualTo(globalVmImageConfig.getSscaArtifactVerification());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(defaultStepConfig.getArtifactoryUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(defaultStepConfig.getGitCloneConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushECRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushACRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushGCRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushGARConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(defaultStepConfig.getS3UploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushDockerRegistryConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(defaultStepConfig.getSecurityConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(defaultStepConfig.getGcsUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_CHECKOV, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmCheckovConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_SEC, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFSecConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_COMPLIANCE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFComplianceConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TF_LINT, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTFLintConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmTerraformConfig().getImage());
    assertThat(
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId).getImage())
        .isEqualTo(defaultStepConfig.getIacmOpenTofuConfig().getImage());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_noConfigsExist() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig defaultVmImageConfig = getVmImageConfig("default");
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaCdxgenOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaArtifactSigning());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaArtifactVerification());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(defaultVmImageConfig.getArtifactoryUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(defaultVmImageConfig.getGitClone());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushECR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushACR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushGCR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushGAR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(defaultVmImageConfig.getS3Upload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushDockerRegistry());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(defaultVmImageConfig.getSecurity());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(defaultVmImageConfig.getGcsUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheGCS());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_onlyAccountLevelConfigExists() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig accountLevelVmImageConfig = getVmImageConfig("accountLevel");
    CIExecutionConfig accountLevelExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(globalAccountId).vmImageConfig(accountLevelVmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_CDXGEN_ORCHESTRATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaCdxgenOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_SIGNING, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaArtifactSigning());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ARTIFACT_VERIFICATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaArtifactVerification());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getArtifactoryUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGitClone());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushECR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushACR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGCR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGAR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getS3Upload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushDockerRegistry());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSecurity());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGcsUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TERRAFORM_PLUGIN, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTerraform());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_OPEN_TOFU_PLUGIN, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmOpenTofu());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_CHECKOV, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmCheckov());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TF_COMPLIANCE, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTFCompliance());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TF_LINT, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTFLint());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM_TF_SEC, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTFSec());
  }

  private static VmImageConfig getVmImageConfig(String level) {
    return VmImageConfig.builder()
        .sscaOrchestration(level + "sscaOrchestrationTag")
        .sscaEnforcement(level + "sscaEnforcementTag")
        .sscaArtifactSigning(level + "sscaArtifactSigningTag")
        .sscaArtifactVerification(level + "sscaArtifactVerificationTag")
        .sscaCdxgenOrchestration(level + "sscaCdxgenOrchestrationTag")
        .slsaVerification(level + "slsaVerificationTag")
        .artifactoryUpload(level + "artifactoryUpload")
        .gitClone(level + "gitClone")
        .buildAndPushACR(level + "buildAndPushACR")
        .buildAndPushECR(level + "buildAndPushECR")
        .buildAndPushGAR(level + "buildAndPushGAR")
        .buildAndPushGCR(level + "buildAndPushGCR")
        .s3Upload(level + "s3Upload")
        .cacheS3(level + "cacheS3")
        .buildAndPushDockerRegistry(level + "buildAndPushDockerRegistry")
        .security(level + "security")
        .gcsUpload(level + "gcsUpload")
        .cacheGCS(level + "cacheGCS")
        .iacmTerraform(level + "iacmTerraform")
        .iacmTerragrunt(level + "iacmTerragrunt")
        .iacmAwsCdk(level + "iacmAwsCdk")
        .buildkit(level + "buildkit")
        .build();
  }

  private static CIStepConfig getDefaultStepeConfig() {
    return CIStepConfig.builder()
        .sscaOrchestrationConfig(getStepImageConfig("defaultsscaOrchestrationTag"))
        .sscaEnforcementConfig(getStepImageConfig("defaultsscaEnforcementTag"))
        .sscaCdxgenOrchestrationConfig(getStepImageConfig("defaultsscaCdxgenOrchestrationTag"))
        .sscaArtifactSigningConfig(getStepImageConfig("defaultsscaArtifactSigningTag"))
        .sscaArtifactVerificationConfig(getStepImageConfig("defaultsscaArtifactVerificationTag"))
        .slsaVerificationConfig(getStepImageConfig("defaultslsaVerificationTag"))
        .provenanceConfig(getStepImageConfig("defaultprovenanceTag"))
        .artifactoryUploadConfig(getStepImageConfig("defaultartifactoryUpload"))
        .gitCloneConfig(getStepImageConfig("defaultgitClone"))
        .buildAndPushACRConfig(getStepImageConfig("defaultbuildAndPushACR"))
        .buildAndPushECRConfig(getStepImageConfig("defaultbuildAndPushECR"))
        .buildAndPushGARConfig(getStepImageConfig("defaultbuildAndPushGAR"))
        .buildAndPushGCRConfig(getStepImageConfig("defaultbuildAndPushGCR"))
        .s3UploadConfig(getStepImageConfig("defaults3Upload"))
        .cacheS3Config(getStepImageConfig("defaultcacheS3"))
        .cacheAzureConfig(getStepImageConfig("defaultcacheAzure"))
        .cacheConfig(getStepImageConfig("defaultcache"))
        .iacmTerragruntConfig(getStepImageConfig("defaultIacmTerragrunt"))
        .iacmAwsCdkConfig(getStepImageConfig("defaultIacmAwsCdk"))
        .buildAndPushDockerRegistryConfig(getStepImageConfig("defaultbuildAndPushDockerRegistry"))
        .securityConfig(getStepImageConfig("defaultsecurity"))
        .gcsUploadConfig(getStepImageConfig("defaultgcsUpload"))
        .cacheGCSConfig(getStepImageConfig("defaultcacheGCS"))
        .iacmTerraformConfig(getStepImageConfig("defaultiacmTerraform"))
        .iacmCheckovConfig(getStepImageConfig("defaultIacmCheckov"))
        .iacmOpenTofuConfig(getStepImageConfig("defaultIacmOpenTofu"))
        .iacmTFComplianceConfig(getStepImageConfig("defaultIacmTfCompliance"))
        .iacmTFLintConfig(getStepImageConfig("defaultIacmTfLint"))
        .iacmTFSecConfig(getStepImageConfig("defaultIacmTfSec"))
        .iacmBlastRadiusAgentConfig(getStepImageConfig("defaultIacmBlastRadiusAgent"))
        .buildkitConfig(getStepImageConfig("defaultbuildkit"))
        .vmImageConfig(getVmImageConfig("default"))
        .build();
  }
  private static CIExecutionConfig getCiExecutionConfig(String level) {
    return CIExecutionConfig.builder()
        .sscaOrchestrationTag(level + "sscaOrchestrationTag")
        .sscaEnforcementTag(level + "sscaEnforcementTag")
        .sscaArtifactVerificationTag(level + "sscaArtifactVerificationTag")
        .sscaArtifactSigningTag(level + "sscaArtifactSigningTag")
        .sscaCdxgenOrchestrationTag(level + "sscaCdxgenOrchestrationTag")
        .provenanceTag(level + "provenanceTag")
        .slsaVerificationTag(level + "slsaVerificationTag")
        .gcsUploadImage(level + "gcsUploadImage")
        .securityImage(level + "securityImage")
        .artifactoryUploadTag(level + "artifactoryUploadTag")
        .buildAndPushGARImage(level + "buildAndPushGARImage")
        .buildAndPushDockerRegistryImage(level + "buildAndPushDockerRegistryImage")
        .buildAndPushGCRImage(level + "buildAndPushGCRImage")
        .buildAndPushECRImage(level + "buildAndPushECRImage")
        .buildAndPushACRImage(level + "buildAndPushACRImage")
        .addOnImage(level + "addOnImage")
        .addOnImageRootless(level + "addOnImageRootless")
        .liteEngineImageRootless(level + "liteEngineImageRootless")
        .liteEngineImage(level + "liteEngineImage")
        .cacheS3Tag(level + "cacheS3Tag")
        .s3UploadImage(level + "s3UploadImage")
        .cacheGCSTag(level + "cacheGCSTag")
        .gitCloneImage(level + "gitCloneImage")
        .iacmOpenTofu(level + "iacmOpenTofu")
        .iacmCheckov(level + "iacmCheckov")
        .iacmTerraform(level + "iacmTerraform")
        .iacmTFCompliance(level + "iacmTFCompliance")
        .iacmTFLint(level + "iacmTFLint")
        .iacmTFSec(level + "iacmTFSec")
        .buildkit(level + "buildkit")
        .build();
  }
  private static StepImageConfig getStepImageConfig(String image) {
    return StepImageConfig.builder()
        .image(image)
        .entrypoint(List.of("entrypoint"))
        .windowsEntrypoint(List.of("windowsEntrypoint"))
        .build();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testUpdateCIContainerTags_sscaPlugins_forVM() {
    VmImageConfig vmImageConfig =
        VmImageConfig.builder().sscaEnforcement("sscaEnforcement").buildAndPushDockerRegistry("docker").build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder().vmImageConfig(vmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();
    Operation operation1 = new Operation();
    operation1.setField(PluginField.SSCA_ENFORCEMENT.getLabel());
    operation1.setValue("tag3");
    Operation operation2 = new Operation();
    operation2.setField(PluginField.SSCA_ORCHESTRATION.getLabel());
    operation2.setValue("tag4");
    operations.add(operation1);
    operations.add(operation2);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.VM);
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushDockerRegistry()).isEqualTo("docker");
    assertThat(executionConfig.getVmImageConfig().getSscaEnforcement()).isEqualTo("tag3");
    assertThat(executionConfig.getVmImageConfig().getSscaOrchestration()).isEqualTo("tag4");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testUpdateCIContainerTags_sscaPlugins_forK8() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .accountIdentifier("acct")
                                            .sscaEnforcementTag("sscaEnforcementTag")
                                            .sscaOrchestrationTag("sscaOrchestrationTag")
                                            .sscaArtifactSigningTag("sscaArtifactSigningTag")
                                            .sscaArtifactVerificationTag("sscaArtifactVerificationTag")
                                            .slsaVerificationTag("slsaVerificationTag")
                                            .provenanceTag("provenanceTag")
                                            .sscaCdxgenOrchestrationTag("sscaCdxgenOrchestrationTag")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();
    Operation operation1 = new Operation();
    operation1.setField(PluginField.PROVENANCE.getLabel());
    operation1.setValue("tag1");
    Operation operation3 = new Operation();
    operation3.setField(PluginField.SSCA_ENFORCEMENT.getLabel());
    operation3.setValue("tag3");
    Operation operation4 = new Operation();
    operation4.setField(PluginField.SSCA_ORCHESTRATION.getLabel());
    operation4.setValue("tag4");
    Operation operation5 = new Operation();
    operation5.setField(PluginField.SLSA_VERIFICATION.getLabel());
    operation5.setValue("tag5");
    Operation operation6 = new Operation();
    operation6.setField(PluginField.SSCA_CDXGEN_ORCHESTRATION.getLabel());
    operation6.setValue("tag6");
    Operation operation7 = new Operation();
    operation7.setField(PluginField.SSCA_ARTIFACT_SIGNING.getLabel());
    operation7.setValue("tag7");
    Operation operation8 = new Operation();
    operation8.setField(PluginField.SSCA_ARTIFACT_VERIFICATION.getLabel());
    operation8.setValue("tag8");
    operations.add(operation1);
    operations.add(operation3);
    operations.add(operation4);
    operations.add(operation5);
    operations.add(operation6);
    operations.add(operation7);
    operations.add(operation8);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.K8);
    assertThat(executionConfig.getAddOnImage()).isEqualTo("addon");
    assertThat(executionConfig.getSscaEnforcementTag()).isEqualTo("tag3");
    assertThat(executionConfig.getSscaOrchestrationTag()).isEqualTo("tag4");
    assertThat(executionConfig.getSlsaVerificationTag()).isEqualTo("tag5");
    assertThat(executionConfig.getProvenanceTag()).isEqualTo("tag1");
    assertThat(executionConfig.getSscaCdxgenOrchestrationTag()).isEqualTo("tag6");
    assertThat(executionConfig.getSscaArtifactSigningTag()).isEqualTo("tag7");
    assertThat(executionConfig.getSscaArtifactVerificationTag()).isEqualTo("tag8");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void getCustomerConfigTestForBuildx() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .buildAndPushBuildxDockerRegistry("dockerBuildxConfig")
                                      .buildAndPushBuildxACR("dockerBuildxAcrConfig")
                                      .buildAndPushBuildxECR("dockerBuildxEcrConfig")
                                      .buildAndPushBuildxGAR("dockerBuildxGarConfig")
                                      .build();
    String accountId = "acct";
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .addOnImageRootless("addonRootless")
                                            .liteEngineImageRootless("liteEngineImageRootless")
                                            .cacheProxyImage("cacheProxy")
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .buildAndPushBuildxDockerRegistryImage("dockerBuildxConfigk8")
                                            .buildAndPushBuildxACRImage("dockerBuildxAcrConfigk8")
                                            .buildAndPushBuildxECRImage("dockerBuildxEcrConfigk8")
                                            .buildAndPushBuildxGARImage("dockerBuildxGarConfigk8")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.of(executionConfig));

    // For VM overrides only
    CIExecutionImages ciExecutionImages =
        ciExecutionConfigService.getCustomerConfig(accountId, StageInfraDetails.Type.VM, true);

    assertThat(ciExecutionImages.getBuildAndPushBuildxDockerRegistryTag()).isEqualTo("dockerBuildxConfig");
    assertThat(ciExecutionImages.getBuildAndPushBuildxACRTag()).isEqualTo("dockerBuildxAcrConfig");
    assertThat(ciExecutionImages.getBuildAndPushBuildxECRTag()).isEqualTo("dockerBuildxEcrConfig");
    assertThat(ciExecutionImages.getBuildAndPushBuildxGARTag()).isEqualTo("dockerBuildxGarConfig");

    // For K8 overrides only
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig(accountId, StageInfraDetails.Type.K8, true);

    assertThat(ciExecutionImages.getBuildAndPushBuildxDockerRegistryTag()).isEqualTo("dockerBuildxConfigk8");
    assertThat(ciExecutionImages.getBuildAndPushBuildxACRTag()).isEqualTo("dockerBuildxAcrConfigk8");
    assertThat(ciExecutionImages.getBuildAndPushBuildxECRTag()).isEqualTo("dockerBuildxEcrConfigk8");
    assertThat(ciExecutionImages.getBuildAndPushBuildxGARTag()).isEqualTo("dockerBuildxGarConfigk8");

    // For VM whole config
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig(accountId, StageInfraDetails.Type.VM, false);

    assertThat(ciExecutionImages.getBuildAndPushBuildxDockerRegistryTag()).isEqualTo("dockerBuildxConfig");
    assertThat(ciExecutionImages.getBuildAndPushBuildxACRTag()).isEqualTo("dockerBuildxAcrConfig");
    assertThat(ciExecutionImages.getBuildAndPushBuildxECRTag()).isEqualTo("dockerBuildxEcrConfig");
    assertThat(ciExecutionImages.getBuildAndPushBuildxGARTag()).isEqualTo("dockerBuildxGarConfig");

    // For K8 whole config
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig(accountId, StageInfraDetails.Type.K8, false);

    assertThat(ciExecutionImages.getBuildAndPushBuildxDockerRegistryTag()).isEqualTo("dockerBuildxConfigk8");
    assertThat(ciExecutionImages.getBuildAndPushBuildxACRTag()).isEqualTo("dockerBuildxAcrConfigk8");
    assertThat(ciExecutionImages.getBuildAndPushBuildxECRTag()).isEqualTo("dockerBuildxEcrConfigk8");
    assertThat(ciExecutionImages.getBuildAndPushBuildxGARTag()).isEqualTo("dockerBuildxGarConfigk8");
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void updateTestForBuildx() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .cacheProxyImage("cacheProxy")
                                            .accountIdentifier("acct")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .addOnImageRootless("addonRootless")
                                            .liteEngineImageRootless("liteEngineImageRootless")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();

    Operation operation1 = new Operation();
    operation1.setField(PluginField.BUILD_PUSH_BUILDX_DOCKER_REGISTRY.getLabel());
    operation1.setValue("dockerBuildxConfig");

    Operation operation2 = new Operation();
    operation2.setField(PluginField.BUILD_PUSH_BUILDX_ACR.getLabel());
    operation2.setValue("dockerBuildxAcrConfig");

    Operation operation3 = new Operation();
    operation3.setField(PluginField.BUILD_PUSH_BUILDX_ECR.getLabel());
    operation3.setValue("dockerBuildxEcrConfig");

    Operation operation4 = new Operation();
    operation4.setField(PluginField.BUILD_PUSH_BUILDX_GAR.getLabel());
    operation4.setValue("dockerBuildxGarConfig");

    operations.add(operation1);
    operations.add(operation2);
    operations.add(operation3);
    operations.add(operation4);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.VM);

    assertThat(executionConfig.getVmImageConfig().getBuildAndPushBuildxDockerRegistry())
        .isEqualTo("dockerBuildxConfig");
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushBuildxACR()).isEqualTo("dockerBuildxAcrConfig");
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushBuildxECR()).isEqualTo("dockerBuildxEcrConfig");
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushBuildxGAR()).isEqualTo("dockerBuildxGarConfig");

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.K8);

    assertThat(executionConfig.getBuildAndPushBuildxDockerRegistryImage()).isEqualTo("dockerBuildxConfig");
    assertThat(executionConfig.getBuildAndPushBuildxGARImage()).isEqualTo("dockerBuildxGarConfig");
    assertThat(executionConfig.getBuildAndPushBuildxECRImage()).isEqualTo("dockerBuildxEcrConfig");
    assertThat(executionConfig.getBuildAndPushBuildxACRImage()).isEqualTo("dockerBuildxAcrConfig");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testExtractSimpleVersionFromImage_ValidNormalization() {
    // Valid normalization cases - should normalize leading zeros
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:01.02.03").get())
        .isEqualTo("1.2.3");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1.17.00").get())
        .isEqualTo("1.17.0");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:000.000.001").get())
        .isEqualTo("0.0.1");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:001.002.003").get())
        .isEqualTo("1.2.3");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:10.05.20").get())
        .isEqualTo("10.5.20");
    // Already valid versions - should return as-is
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1.2.3").get())
        .isEqualTo("1.2.3");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:0.0.1").get())
        .isEqualTo("0.0.1");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:10.15.22").get())
        .isEqualTo("10.15.22");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testExtractSimpleVersionFromImage_InvalidMalformed() {
    // Missing components
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1.2").get())
        .isEqualTo("");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1").get())
        .isEqualTo("");
    // Non-numeric components
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:abc.def.ghi").get())
        .isEqualTo("");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1.2.abc").get())
        .isEqualTo("");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1.abc.3").get())
        .isEqualTo("");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:abc.2.3").get())
        .isEqualTo("");
    // Mixed alphanumeric
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1a.2b.3c").get())
        .isEqualTo("");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testExtractSimpleVersionFromImage_EdgeCases() {
    // Negative numbers (after normalization, these should be invalid)
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1.2.-1").get())
        .isEqualTo("");
    // Empty version components
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:..").get())
        .isEqualTo("");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:1..3").get())
        .isEqualTo("");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:.2.3").get())
        .isEqualTo("");
    // Zero-only versions
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:0.0.0").get())
        .isEqualTo("0.0.0");
    assertThat((String) on(ciExecutionConfigService).call("extractSimpleVersionFromImage", "addon:00.00.00").get())
        .isEqualTo("0.0.0");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testUpdateCIContainerTags_IACMPlugins_forK8() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .accountIdentifier("acct")
                                            .iacmTerraform("iacmTerraform:1.0.0")
                                            .iacmTerragrunt("iacmTerragrunt:1.0.0")
                                            .iacmAwsCdk("iacmAwsCdk:1.0.0")
                                            .iacmAnsible("iacmAnsible:1.0.0")
                                            .iacmOpenTofu("iacmOpenTofu:1.0.0")
                                            .iacmCheckov("iacmCheckov:1.0.0")
                                            .iacmTFCompliance("iacmTFCompliance:1.0.0")
                                            .iacmTFLint("iacmTFLint:1.0.0")
                                            .iacmTFSec("iacmTFSec:1.0.0")
                                            .iacmModuleTest("iacmModuleTest:1.0.0")
                                            .iacmBlastRadiusAgent("iacmBlastRadiusAgent:1.0.0")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();

    // Test all IACM fields including the newly added ones
    Operation opTerraform = new Operation();
    opTerraform.setField(PluginField.IACM_TERRAFORM.getLabel());
    opTerraform.setValue("iacmTerraform:2.0.0");
    operations.add(opTerraform);

    Operation opTerragrunt = new Operation();
    opTerragrunt.setField(PluginField.IACM_TERRAGRUNT.getLabel());
    opTerragrunt.setValue("iacmTerragrunt:2.0.0");
    operations.add(opTerragrunt);

    Operation opAwsCdk = new Operation();
    opAwsCdk.setField(PluginField.IACM_AWS_CDK.getLabel());
    opAwsCdk.setValue("iacmAwsCdk:2.0.0");
    operations.add(opAwsCdk);

    Operation opAnsible = new Operation();
    opAnsible.setField(PluginField.IACM_ANSIBLE.getLabel());
    opAnsible.setValue("iacmAnsible:2.0.0");
    operations.add(opAnsible);

    Operation opOpenTofu = new Operation();
    opOpenTofu.setField(PluginField.IACM_OPENTOFU.getLabel());
    opOpenTofu.setValue("iacmOpenTofu:2.0.0");
    operations.add(opOpenTofu);

    Operation opCheckov = new Operation();
    opCheckov.setField(PluginField.IACM_CHECKOV.getLabel());
    opCheckov.setValue("iacmCheckov:2.0.0");
    operations.add(opCheckov);

    Operation opTFCompliance = new Operation();
    opTFCompliance.setField(PluginField.IACM_TF_COMPLIANCE.getLabel());
    opTFCompliance.setValue("iacmTFCompliance:2.0.0");
    operations.add(opTFCompliance);

    Operation opTFLint = new Operation();
    opTFLint.setField(PluginField.IACM_TF_LINT.getLabel());
    opTFLint.setValue("iacmTFLint:2.0.0");
    operations.add(opTFLint);

    Operation opTFSec = new Operation();
    opTFSec.setField(PluginField.IACM_TF_SEC.getLabel());
    opTFSec.setValue("iacmTFSec:2.0.0");
    operations.add(opTFSec);

    Operation opModuleTest = new Operation();
    opModuleTest.setField(PluginField.IACM_MODULE_TEST.getLabel());
    opModuleTest.setValue("iacmModuleTest:2.0.0");
    operations.add(opModuleTest);

    Operation opBlastRadiusAgent = new Operation();
    opBlastRadiusAgent.setField(PluginField.IACM_BLAST_RADIUS_AGENT.getLabel());
    opBlastRadiusAgent.setValue("iacmBlastRadiusAgent:2.0.0");
    operations.add(opBlastRadiusAgent);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.K8);

    // Verify all IACM fields were updated correctly
    assertThat(executionConfig.getIacmTerraform()).isEqualTo("iacmTerraform:2.0.0");
    assertThat(executionConfig.getIacmTerragrunt()).isEqualTo("iacmTerragrunt:2.0.0");
    assertThat(executionConfig.getIacmAwsCdk()).isEqualTo("iacmAwsCdk:2.0.0");
    assertThat(executionConfig.getIacmAnsible()).isEqualTo("iacmAnsible:2.0.0");
    assertThat(executionConfig.getIacmOpenTofu()).isEqualTo("iacmOpenTofu:2.0.0");
    assertThat(executionConfig.getIacmCheckov()).isEqualTo("iacmCheckov:2.0.0");
    assertThat(executionConfig.getIacmTFCompliance()).isEqualTo("iacmTFCompliance:2.0.0");
    assertThat(executionConfig.getIacmTFLint()).isEqualTo("iacmTFLint:2.0.0");
    assertThat(executionConfig.getIacmTFSec()).isEqualTo("iacmTFSec:2.0.0");
    assertThat(executionConfig.getIacmModuleTest()).isEqualTo("iacmModuleTest:2.0.0");
    assertThat(executionConfig.getIacmBlastRadiusAgent()).isEqualTo("iacmBlastRadiusAgent:2.0.0");

    // Verify other fields weren't affected
    assertThat(executionConfig.getAddOnImage()).isEqualTo("addon");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testUpdateCIContainerTags_IACMPlugins_forVM() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .iacmTerraform("iacmTerraform:1.0.0")
                                      .iacmTerragrunt("iacmTerragrunt:1.0.0")
                                      .iacmAwsCdk("iacmAwsCdk:1.0.0")
                                      .iacmAnsible("iacmAnsible:1.0.0")
                                      .iacmOpenTofu("iacmOpenTofu:1.0.0")
                                      .iacmCheckov("iacmCheckov:1.0.0")
                                      .iacmTFCompliance("iacmTFCompliance:1.0.0")
                                      .iacmTFLint("iacmTFLint:1.0.0")
                                      .iacmTFSec("iacmTFSec:1.0.0")
                                      .iacmModuleTest("iacmModuleTest:1.0.0")
                                      .iacmBlastRadiusAgent("iacmBlastRadiusAgent:1.0.0")
                                      .buildAndPushDockerRegistry("docker")
                                      .build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder().vmImageConfig(vmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();

    // Test all IACM fields for VM including the newly added ones
    Operation opTerraform = new Operation();
    opTerraform.setField(PluginField.IACM_TERRAFORM.getLabel());
    opTerraform.setValue("iacmTerraform:3.0.0");
    operations.add(opTerraform);

    Operation opTerragrunt = new Operation();
    opTerragrunt.setField(PluginField.IACM_TERRAGRUNT.getLabel());
    opTerragrunt.setValue("iacmTerragrunt:3.0.0");
    operations.add(opTerragrunt);

    Operation opAwsCdk = new Operation();
    opAwsCdk.setField(PluginField.IACM_AWS_CDK.getLabel());
    opAwsCdk.setValue("iacmAwsCdk:3.0.0");
    operations.add(opAwsCdk);

    Operation opAnsible = new Operation();
    opAnsible.setField(PluginField.IACM_ANSIBLE.getLabel());
    opAnsible.setValue("iacmAnsible:3.0.0");
    operations.add(opAnsible);

    Operation opOpenTofu = new Operation();
    opOpenTofu.setField(PluginField.IACM_OPENTOFU.getLabel());
    opOpenTofu.setValue("iacmOpenTofu:3.0.0");
    operations.add(opOpenTofu);

    Operation opCheckov = new Operation();
    opCheckov.setField(PluginField.IACM_CHECKOV.getLabel());
    opCheckov.setValue("iacmCheckov:3.0.0");
    operations.add(opCheckov);

    Operation opTFCompliance = new Operation();
    opTFCompliance.setField(PluginField.IACM_TF_COMPLIANCE.getLabel());
    opTFCompliance.setValue("iacmTFCompliance:3.0.0");
    operations.add(opTFCompliance);

    Operation opTFLint = new Operation();
    opTFLint.setField(PluginField.IACM_TF_LINT.getLabel());
    opTFLint.setValue("iacmTFLint:3.0.0");
    operations.add(opTFLint);

    Operation opTFSec = new Operation();
    opTFSec.setField(PluginField.IACM_TF_SEC.getLabel());
    opTFSec.setValue("iacmTFSec:3.0.0");
    operations.add(opTFSec);

    Operation opModuleTest = new Operation();
    opModuleTest.setField(PluginField.IACM_MODULE_TEST.getLabel());
    opModuleTest.setValue("iacmModuleTest:3.0.0");
    operations.add(opModuleTest);

    Operation opBlastRadiusAgent = new Operation();
    opBlastRadiusAgent.setField(PluginField.IACM_BLAST_RADIUS_AGENT.getLabel());
    opBlastRadiusAgent.setValue("iacmBlastRadiusAgent:3.0.0");
    operations.add(opBlastRadiusAgent);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.VM);

    // Verify all IACM fields were updated correctly for VM
    assertThat(executionConfig.getVmImageConfig().getIacmTerraform()).isEqualTo("iacmTerraform:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmTerragrunt()).isEqualTo("iacmTerragrunt:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmAwsCdk()).isEqualTo("iacmAwsCdk:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmAnsible()).isEqualTo("iacmAnsible:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmOpenTofu()).isEqualTo("iacmOpenTofu:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmCheckov()).isEqualTo("iacmCheckov:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmTFCompliance()).isEqualTo("iacmTFCompliance:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmTFLint()).isEqualTo("iacmTFLint:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmTFSec()).isEqualTo("iacmTFSec:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmModuleTest()).isEqualTo("iacmModuleTest:3.0.0");
    assertThat(executionConfig.getVmImageConfig().getIacmBlastRadiusAgent()).isEqualTo("iacmBlastRadiusAgent:3.0.0");

    // Verify other fields weren't affected
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushDockerRegistry()).isEqualTo("docker");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testGetDefaultConfig_IACMPlugins_forK8() {
    // Verify that getDefaultConfig returns successfully for K8 and returns a valid object
    CIExecutionImages images = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.K8);
    assertThat(images).isNotNull();

    // The actual default values depend on the test configuration
    // The important thing is that the method executes without errors
    // and returns the configured defaults (even if some are null in test environment)
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetDefaultConfig_EcsMatchesK8HarnessImages() {
    CIExecutionImages ecsDefaults = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.ECS);
    CIExecutionImages k8Defaults = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.K8);
    assertThat(ecsDefaults).isNotNull().isEqualTo(k8Defaults);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testGetDefaultConfig_IACMPlugins_forVM() {
    // Verify that getDefaultConfig returns successfully for VM and returns a valid object
    CIExecutionImages images = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.VM);
    assertThat(images).isNotNull();

    // The actual default values depend on the test configuration
    // The important thing is that the method executes without errors
    // and returns the configured defaults (even if some are null in test environment)
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testPluginFieldLabels_IACMFields() {
    // Verify that PluginField labels match the CIExecutionImages field names
    assertThat(PluginField.IACM_TERRAFORM.getLabel()).isEqualTo("iacmTerraform");
    assertThat(PluginField.IACM_TERRAGRUNT.getLabel()).isEqualTo("iacmTerragrunt");
    assertThat(PluginField.IACM_AWS_CDK.getLabel()).isEqualTo("iacmAwsCdk");
    assertThat(PluginField.IACM_ANSIBLE.getLabel()).isEqualTo("iacmAnsible");
    assertThat(PluginField.IACM_OPENTOFU.getLabel()).isEqualTo("iacmOpenTofu");
    assertThat(PluginField.IACM_CHECKOV.getLabel()).isEqualTo("iacmCheckov");
    assertThat(PluginField.IACM_TF_SEC.getLabel()).isEqualTo("iacmTFSec");
    assertThat(PluginField.IACM_TF_LINT.getLabel()).isEqualTo("iacmTFLint");
    assertThat(PluginField.IACM_TF_COMPLIANCE.getLabel()).isEqualTo("iacmTFCompliance");
    assertThat(PluginField.IACM_MODULE_TEST.getLabel()).isEqualTo("iacmModuleTest");

    // Verify that alternate labels are set for backward compatibility (LEGACY format)
    assertThat(PluginField.IACM_TERRAFORM.getAlternateLabel()).isEqualTo("IACMTerraformTag");
    assertThat(PluginField.IACM_TERRAGRUNT.getAlternateLabel()).isEqualTo("IACMTerragruntTag");
    assertThat(PluginField.IACM_AWS_CDK.getAlternateLabel()).isEqualTo("IACMAwsCdkTag");
    assertThat(PluginField.IACM_ANSIBLE.getAlternateLabel()).isEqualTo("IACMAnsibleTag");
    assertThat(PluginField.IACM_OPENTOFU.getAlternateLabel()).isEqualTo("IACMOpentofuTag");
    assertThat(PluginField.IACM_CHECKOV.getAlternateLabel()).isEqualTo("IACMCheckovTag");
    assertThat(PluginField.IACM_BLAST_RADIUS_AGENT.getAlternateLabel()).isEqualTo("IACMBlastRadiusAgentTag");
    assertThat(PluginField.IACM_REMEDIATION_AGENT.getAlternateLabel()).isEqualTo("IACMRemediationAgentTag");
    assertThat(PluginField.IACM_TF_SEC.getAlternateLabel()).isEqualTo("IACMTfSecTag");
    assertThat(PluginField.IACM_TF_LINT.getAlternateLabel()).isEqualTo("IACMTfLintTag");
    assertThat(PluginField.IACM_TF_COMPLIANCE.getAlternateLabel()).isEqualTo("IACMTfComplianceTag");
    assertThat(PluginField.IACM_MODULE_TEST.getAlternateLabel()).isEqualTo("IACMModuleTestTag");

    // Verify that getPluginField can find all IACM fields by their NEW labels (current format)
    assertThat(PluginField.getPluginField("iacmTerraform")).isEqualTo(PluginField.IACM_TERRAFORM);
    assertThat(PluginField.getPluginField("iacmTerragrunt")).isEqualTo(PluginField.IACM_TERRAGRUNT);
    assertThat(PluginField.getPluginField("iacmAwsCdk")).isEqualTo(PluginField.IACM_AWS_CDK);
    assertThat(PluginField.getPluginField("iacmAnsible")).isEqualTo(PluginField.IACM_ANSIBLE);
    assertThat(PluginField.getPluginField("iacmOpenTofu")).isEqualTo(PluginField.IACM_OPENTOFU);
    assertThat(PluginField.getPluginField("iacmCheckov")).isEqualTo(PluginField.IACM_CHECKOV);
    assertThat(PluginField.getPluginField("iacmBlastRadiusAgent")).isEqualTo(PluginField.IACM_BLAST_RADIUS_AGENT);
    assertThat(PluginField.getPluginField("iacmRemediationAgent")).isEqualTo(PluginField.IACM_REMEDIATION_AGENT);
    assertThat(PluginField.getPluginField("iacmTFSec")).isEqualTo(PluginField.IACM_TF_SEC);
    assertThat(PluginField.getPluginField("iacmTFLint")).isEqualTo(PluginField.IACM_TF_LINT);
    assertThat(PluginField.getPluginField("iacmTFCompliance")).isEqualTo(PluginField.IACM_TF_COMPLIANCE);
    assertThat(PluginField.getPluginField("iacmModuleTest")).isEqualTo(PluginField.IACM_MODULE_TEST);

    // BACKWARD COMPATIBILITY: Verify that getPluginField can ALSO find IACM fields by LEGACY labels
    assertThat(PluginField.getPluginField("IACMTerraformTag")).isEqualTo(PluginField.IACM_TERRAFORM);
    assertThat(PluginField.getPluginField("IACMTerragruntTag")).isEqualTo(PluginField.IACM_TERRAGRUNT);
    assertThat(PluginField.getPluginField("IACMAwsCdkTag")).isEqualTo(PluginField.IACM_AWS_CDK);
    assertThat(PluginField.getPluginField("IACMAnsibleTag")).isEqualTo(PluginField.IACM_ANSIBLE);
    assertThat(PluginField.getPluginField("IACMOpentofuTag")).isEqualTo(PluginField.IACM_OPENTOFU);
    assertThat(PluginField.getPluginField("IACMCheckovTag")).isEqualTo(PluginField.IACM_CHECKOV);
    assertThat(PluginField.getPluginField("IACMBlastRadiusAgentTag")).isEqualTo(PluginField.IACM_BLAST_RADIUS_AGENT);
    assertThat(PluginField.getPluginField("IACMRemediationAgentTag")).isEqualTo(PluginField.IACM_REMEDIATION_AGENT);
    assertThat(PluginField.getPluginField("IACMTfSecTag")).isEqualTo(PluginField.IACM_TF_SEC);
    assertThat(PluginField.getPluginField("IACMTfLintTag")).isEqualTo(PluginField.IACM_TF_LINT);
    assertThat(PluginField.getPluginField("IACMTfComplianceTag")).isEqualTo(PluginField.IACM_TF_COMPLIANCE);
    assertThat(PluginField.getPluginField("IACMModuleTestTag")).isEqualTo(PluginField.IACM_MODULE_TEST);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testGetCustomerConfig_IACMPlugins_forK8() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .iacmTerraform("iacmTerraform:custom")
                                            .iacmTerragrunt("iacmTerragrunt:custom")
                                            .iacmAwsCdk("iacmAwsCdk:custom")
                                            .iacmAnsible("iacmAnsible:custom")
                                            .iacmOpenTofu("iacmOpenTofu:custom")
                                            .iacmCheckov("iacmCheckov:custom")
                                            .iacmTFCompliance("iacmTFCompliance:custom")
                                            .iacmTFLint("iacmTFLint:custom")
                                            .iacmTFSec("iacmTFSec:custom")
                                            .iacmModuleTest("iacmModuleTest:custom")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    CIExecutionImages images = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.K8, true);

    // Verify all custom IACM fields are returned for K8
    assertThat(images.getIacmTerraform()).isEqualTo("iacmTerraform:custom");
    assertThat(images.getIacmTerragrunt()).isEqualTo("iacmTerragrunt:custom");
    assertThat(images.getIacmAwsCdk()).isEqualTo("iacmAwsCdk:custom");
    assertThat(images.getIacmAnsible()).isEqualTo("iacmAnsible:custom");
    assertThat(images.getIacmOpenTofu()).isEqualTo("iacmOpenTofu:custom");
    assertThat(images.getIacmCheckov()).isEqualTo("iacmCheckov:custom");
    assertThat(images.getIacmTFCompliance()).isEqualTo("iacmTFCompliance:custom");
    assertThat(images.getIacmTFLint()).isEqualTo("iacmTFLint:custom");
    assertThat(images.getIacmTFSec()).isEqualTo("iacmTFSec:custom");
    assertThat(images.getIacmModuleTest()).isEqualTo("iacmModuleTest:custom");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testGetCustomerConfig_IACMPlugins_forVM() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .iacmTerraform("iacmTerraform:custom-vm")
                                      .iacmTerragrunt("iacmTerragrunt:custom-vm")
                                      .iacmAwsCdk("iacmAwsCdk:custom-vm")
                                      .iacmAnsible("iacmAnsible:custom-vm")
                                      .iacmOpenTofu("iacmOpenTofu:custom-vm")
                                      .iacmCheckov("iacmCheckov:custom-vm")
                                      .iacmTFCompliance("iacmTFCompliance:custom-vm")
                                      .iacmTFLint("iacmTFLint:custom-vm")
                                      .iacmTFSec("iacmTFSec:custom-vm")
                                      .iacmModuleTest("iacmModuleTest:custom-vm")
                                      .build();
    CIExecutionConfig executionConfig =
        CIExecutionConfig.builder().accountIdentifier("acct").vmImageConfig(vmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    CIExecutionImages images = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.VM, true);

    // Verify all custom IACM fields are returned for VM
    assertThat(images.getIacmTerraform()).isEqualTo("iacmTerraform:custom-vm");
    assertThat(images.getIacmTerragrunt()).isEqualTo("iacmTerragrunt:custom-vm");
    assertThat(images.getIacmAwsCdk()).isEqualTo("iacmAwsCdk:custom-vm");
    assertThat(images.getIacmAnsible()).isEqualTo("iacmAnsible:custom-vm");
    assertThat(images.getIacmOpenTofu()).isEqualTo("iacmOpenTofu:custom-vm");
    assertThat(images.getIacmCheckov()).isEqualTo("iacmCheckov:custom-vm");
    assertThat(images.getIacmTFCompliance()).isEqualTo("iacmTFCompliance:custom-vm");
    assertThat(images.getIacmTFLint()).isEqualTo("iacmTFLint:custom-vm");
    assertThat(images.getIacmTFSec()).isEqualTo("iacmTFSec:custom-vm");
    assertThat(images.getIacmModuleTest()).isEqualTo("iacmModuleTest:custom-vm");
  }
}
