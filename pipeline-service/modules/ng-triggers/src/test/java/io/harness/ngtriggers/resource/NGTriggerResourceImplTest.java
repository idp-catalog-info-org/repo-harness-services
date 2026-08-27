/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.LastTriggerExecutionDetails;
import io.harness.ngtriggers.beans.dto.NGTriggerCatalogDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerYamlRequestDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.metadata.CronMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.instrumentation.TriggerTelemetryHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class NGTriggerResourceImplTest extends CategoryTest {
  @Mock NGTriggerService ngTriggerService;
  @Mock NGTriggerEventsService ngTriggerEventsService;
  @Mock NGSettingsClient settingsClient;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock FilterService filterService;
  @InjectMocks NGTriggerResourceImpl ngTriggerResource;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock TriggerTelemetryHelper triggerTelemetryHelper;
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private String ngTriggerYaml;
  private String ngTriggerYamlWithGitSync;
  private String ngTriggerYamlWithTimezone;
  private String ngTriggerYamlGitlabMRComment;
  private String ngTriggerYamlBitbucketPRComment;

  private NGTriggerDetailsResponseDTO ngTriggerDetailsResponseDTO;
  private NGTriggerResponseDTO ngTriggerResponseDTO;

  private NGTriggerResponseDTO ngTriggerErrorDTO;
  private NGTriggerResponseDTO ngTriggerResponseDTOGitSync;
  private NGTriggerResponseDTO ngTriggerResponseDTOGitlabMRComment;
  private NGTriggerResponseDTO ngTriggerResponseDTOBitbucketPRComment;
  private NGTriggerEntity ngTriggerEntity;
  private NGTriggerEntity ngTriggerEntityGitSync;
  private NGTriggerEntity ngTriggerEntityGitlabMRComment;
  private NGTriggerEntity ngTriggerEntityBitbucketPRComment;
  private NGTriggerEntity ngTriggerEntityWithTimezone;
  private NGTriggerConfigV2 ngTriggerConfig;
  private NGTriggerConfigV2 ngTriggerConfigWithTimezone;

  private static NGTriggerYamlRequestDTO yamlReq(String yaml) {
    return NGTriggerYamlRequestDTO.builder().yaml(yaml).build();
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    when(settingsClient.getSetting(MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(request);
    when(settingsClient.getSetting(MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION, "", "", "")).thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-github-pr-v2.yaml";
    String filenameGitSync = "ng-trigger-github-pr-gitsync.yaml";
    ngTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerYamlWithGitSync =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameGitSync)), StandardCharsets.UTF_8);
    ngTriggerYamlGitlabMRComment =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-gitlab-mr-comment-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYamlWithTimezone =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-cron-v2-with-timezone.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    WebhookMetadata metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();
    NGTriggerMetadata ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    ngTriggerResponseDTO = NGTriggerResponseDTO.builder()
                               .accountIdentifier(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .targetIdentifier(PIPELINE_IDENTIFIER)
                               .identifier(IDENTIFIER)
                               .name(NAME)
                               .yaml(ngTriggerYaml)
                               .type(NGTriggerType.WEBHOOK)
                               .version(0L)
                               .build();

    ngTriggerResponseDTOGitSync = NGTriggerResponseDTO.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .targetIdentifier(PIPELINE_IDENTIFIER)
                                      .identifier(IDENTIFIER)
                                      .name(NAME)
                                      .yaml(ngTriggerYamlWithGitSync)
                                      .type(NGTriggerType.WEBHOOK)
                                      .version(0L)
                                      .build();

    ngTriggerResponseDTOGitlabMRComment = NGTriggerResponseDTO.builder()
                                              .accountIdentifier(ACCOUNT_ID)
                                              .orgIdentifier(ORG_IDENTIFIER)
                                              .projectIdentifier(PROJ_IDENTIFIER)
                                              .targetIdentifier(PIPELINE_IDENTIFIER)
                                              .identifier(IDENTIFIER)
                                              .name(NAME)
                                              .yaml(ngTriggerYamlGitlabMRComment)
                                              .type(NGTriggerType.WEBHOOK)
                                              .version(0L)
                                              .build();

    ngTriggerResponseDTOBitbucketPRComment = NGTriggerResponseDTO.builder()
                                                 .accountIdentifier(ACCOUNT_ID)
                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                 .targetIdentifier(PIPELINE_IDENTIFIER)
                                                 .identifier(IDENTIFIER)
                                                 .name(NAME)
                                                 .yaml(ngTriggerYamlBitbucketPRComment)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    ngTriggerDetailsResponseDTO =
        NGTriggerDetailsResponseDTO.builder()
            .name(NAME)
            .identifier(IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .lastTriggerExecutionDetails(LastTriggerExecutionDetails.builder()
                                             .lastExecutionTime(1607306091861L)
                                             .lastExecutionStatus("SUCCESS")
                                             .lastExecutionSuccessful(false)
                                             .planExecutionId("PYV86FtaSfes7uPrGYJhBg")
                                             .message("Pipeline execution was requested successfully")
                                             .build())
            .webhookDetails(WebhookDetails.builder().webhookSourceRepo("Github").build())
            .enabled(true)
            .isPipelineInputOutdated(false)
            .yamlVersion("0")
            .build();

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .targetIdentifier(PIPELINE_IDENTIFIER)
                          .identifier(IDENTIFIER)
                          .name(NAME)
                          .targetType(TargetType.PIPELINE)
                          .type(NGTriggerType.WEBHOOK)
                          .metadata(ngTriggerMetadata)
                          .yaml(ngTriggerYaml)
                          .version(0L)
                          .build();
    ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .targetIdentifier(PIPELINE_IDENTIFIER)
                                 .identifier(IDENTIFIER)
                                 .name(NAME)
                                 .targetType(TargetType.PIPELINE)
                                 .type(NGTriggerType.WEBHOOK)
                                 .metadata(ngTriggerMetadata)
                                 .yaml(ngTriggerYamlWithGitSync)
                                 .version(0L)
                                 .build();

    ngTriggerEntityGitlabMRComment = NGTriggerEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJ_IDENTIFIER)
                                         .targetIdentifier(PIPELINE_IDENTIFIER)
                                         .identifier(IDENTIFIER)
                                         .name(NAME)
                                         .targetType(TargetType.PIPELINE)
                                         .type(NGTriggerType.WEBHOOK)
                                         .metadata(ngTriggerMetadata)
                                         .yaml(ngTriggerYamlGitlabMRComment)
                                         .version(0L)
                                         .build();

    ngTriggerEntityBitbucketPRComment = NGTriggerEntity.builder()
                                            .accountId(ACCOUNT_ID)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .projectIdentifier(PROJ_IDENTIFIER)
                                            .targetIdentifier(PIPELINE_IDENTIFIER)
                                            .identifier(IDENTIFIER)
                                            .name(NAME)
                                            .targetType(TargetType.PIPELINE)
                                            .type(NGTriggerType.WEBHOOK)
                                            .metadata(ngTriggerMetadata)
                                            .yaml(ngTriggerYamlBitbucketPRComment)
                                            .version(0L)
                                            .build();
    ngTriggerConfigWithTimezone = YamlPipelineUtils.read(ngTriggerYamlWithTimezone, NGTriggerConfigV2.class);
    ScheduledTriggerConfig scheduledTriggerConfig =
        (ScheduledTriggerConfig) ngTriggerConfigWithTimezone.getSource().getSpec();
    CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
    String cronExpressionType = StringUtils.isBlank(cronTriggerSpec.getType()) ? "UNIX" : cronTriggerSpec.getType();
    CronMetadata cronMetadata = CronMetadata.builder()
                                    .expression(cronTriggerSpec.getExpression())
                                    .type(cronExpressionType)
                                    .timezone(ZoneId.of("GMT"))
                                    .build();
    NGTriggerMetadata ngTriggerCronMetadata = NGTriggerMetadata.builder().cron(cronMetadata).build();
    ngTriggerEntityWithTimezone = NGTriggerEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .targetIdentifier(PIPELINE_IDENTIFIER)
                                      .identifier(IDENTIFIER)
                                      .name(NAME)
                                      .targetType(TargetType.PIPELINE)
                                      .type(NGTriggerType.SCHEDULED)
                                      .metadata(ngTriggerCronMetadata)
                                      .yaml(ngTriggerYaml)
                                      .version(0L)
                                      .build();

    ngTriggerErrorDTO = NGTriggerResponseDTO.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .targetIdentifier(PIPELINE_IDENTIFIER)
                            .identifier(IDENTIFIER)
                            .name(NAME)
                            .yaml(ngTriggerYaml)
                            .type(NGTriggerType.WEBHOOK)
                            .errorResponse(true)
                            .version(0L)
                            .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreate() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
             eq(PIPELINE_IDENTIFIER), eq(ngTriggerYaml), isNull(), eq(true), eq(false), isNull()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTO));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYaml), true, false, null)
                                           .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateFailsWhenEnforceExecutorEnabledButExecutorMissing() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenThrow(new InvalidRequestException(
            "executorInfo with uuid and type (USER or SERVICE_ACCOUNT) is required when executor identity "
            + "enforcement is enabled."));

    assertThatThrownBy(()
                           -> ngTriggerResource.createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, yamlReq(ngTriggerYaml), true, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("executorInfo with uuid and type");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateCheckAccess() throws IOException {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTO));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYaml), true, false, null)
                                           .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
    verify(ngTriggerService, times(1))
        .createTriggerWithValidation(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());

    ngTriggerResource
        .createV2(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, yamlReq(ngTriggerYaml), true, false, null)
        .getData();
    verify(ngTriggerService, times(2))
        .createTriggerWithValidation(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());

    ngTriggerResource
        .createV2(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, yamlReq(ngTriggerYaml), true, false, null)
        .getData();
    verify(ngTriggerService, times(3))
        .createTriggerWithValidation(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateInvalidYamlError() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse(ngTriggerErrorDTO));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYaml), true, false, null)
                                           .getData();
    assertThat(responseDTO.isErrorResponse()).isEqualTo(true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateException() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenThrow(new InvalidRequestException("exception"));
    ngTriggerResource
        .createV2(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, yamlReq(ngTriggerYaml), true, false, null)
        .getData();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testCreateWithGitSync() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTOGitSync));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYaml), false, false, null)
                                           .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOGitSync);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateWithTimezone() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
             eq(PIPELINE_IDENTIFIER), eq(ngTriggerYamlWithTimezone), isNull(), eq(true), eq(false), isNull()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTO));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYamlWithTimezone), true, false, null)
                                           .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, true);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity, null, true)).thenReturn(ngTriggerResponseDTO);
    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetException() {
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, true);
    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerDetailsNotPresent() {
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, true);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity, null, true)).thenReturn(null);
    assertThatThrownBy(()
                           -> ngTriggerResource.getTriggerDetails(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, PIPELINE_IDENTIFIER, null))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(
            "Trigger " + IDENTIFIER + " does not exist in project " + PROJ_IDENTIFIER + " in org " + ORG_IDENTIFIER);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerDetails() throws IOException {
    NGTriggerConfigV2 ngTriggerConfigV2 = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, true);

    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, true);
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(ngTriggerConfigV2).build();
    when(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, true, true, false, true, null, true))
        .thenReturn(ngTriggerDetailsResponseDTO);

    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml,
            false, null, true);

    NGTriggerDetailsResponseDTO responseDTO =
        ngTriggerResource
            .getTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, PIPELINE_IDENTIFIER, null)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerDetailsResponseDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdate() throws Exception {
    when(ngTriggerService.updateTriggerWithValidation(eq("0"), eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
             eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER), eq(ngTriggerYaml), isNull(), eq(true), isNull()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTO));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, yamlReq(ngTriggerYaml), true, null)
                                           .getData();

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateAccess() throws Exception {
    when(ngTriggerService.updateTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTO));
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, yamlReq(ngTriggerYaml), true, null)
                                           .getData();

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
    verify(ngTriggerService, times(1))
        .updateTriggerWithValidation(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());

    ngTriggerResource
        .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER,
            yamlReq(ngTriggerYaml), true, null)
        .getData();
    verify(ngTriggerService, times(2))
        .updateTriggerWithValidation(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());

    ngTriggerResource
        .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER,
            yamlReq(ngTriggerYaml), true, null)
        .getData();
    verify(ngTriggerService, times(3))
        .updateTriggerWithValidation(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateNotPresent() throws Exception {
    when(ngTriggerService.updateTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenThrow(new EntityNotFoundException(String.format("Trigger %s does not exist", IDENTIFIER)));

    ngTriggerResource
        .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER,
            yamlReq(ngTriggerYaml), true, null)
        .getData();
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateInvalidYamlError() throws Exception {
    when(ngTriggerService.updateTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse(ngTriggerErrorDTO));

    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, yamlReq(ngTriggerYaml), true, null)
                                           .getData();

    assertThat(responseDTO.isErrorResponse()).isEqualTo(true);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateException() throws Exception {
    when(ngTriggerService.updateTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenThrow(new EntityNotFoundException("exception"));

    ngTriggerResource
        .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER,
            yamlReq(ngTriggerYaml), true, null)
        .getData();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateWithGitSync() throws Exception {
    when(ngTriggerService.updateTriggerWithValidation(eq("0"), eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
             eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER), eq(ngTriggerYamlWithGitSync), isNull(), eq(false), isNull()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTOGitSync));
    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .updateV2("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER,
                yamlReq(ngTriggerYamlWithGitSync), false, null)
            .getData();

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOGitSync);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true)
        .when(ngTriggerService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, null, true);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity, null, true)).thenReturn(ngTriggerResponseDTO);
    Boolean response =
        ngTriggerResource
            .delete(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null)
            .getData();

    assertThat(response).isTrue();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteTriggerException() {
    doThrow(new InvalidRequestException(String.format("NGTrigger [%s] couldn't be deleted", IDENTIFIER)))
        .when(ngTriggerService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, null, true);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity, null, true)).thenReturn(ngTriggerResponseDTO);
    ngTriggerResource.delete(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null)
        .getData();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testListServicesWithDESCSort() {
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("uniqueId").build();
    Criteria criteria =
        TriggerFilterHelper.createCriteriaForGetList("", "", "", "", null, "", "", null, null, scopeInfo, true);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, NGTriggerEntityKeys.createdAt));
    final Page<NGTriggerEntity> serviceList = new PageImpl<>(Collections.singletonList(ngTriggerEntity), pageable, 1);
    doReturn(serviceList).when(ngTriggerService).list(criteria, pageable);

    when(filterService.get("", "", "", "", FilterType.TRIGGER)).thenReturn(FilterDTO.builder().build());
    when(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(
             ngTriggerEntity, true, false, false, true, scopeInfo, true))
        .thenReturn(ngTriggerDetailsResponseDTO);

    List<NGTriggerDetailsResponseDTO> content =
        ngTriggerResource.getListForTarget("", "", "", "", "", 0, 10, null, "", null, scopeInfo).getData().getContent();

    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0).getName()).isEqualTo(ngTriggerDetailsResponseDTO.getName());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGitConnectorTrigger() throws IOException {
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    assertThat(webhookTriggerConfig.getSpec().fetchGitAware().fetchConnectorRef()).isEqualTo("conn");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTrigger() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-cron-v2.yaml";
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(triggerYaml, NGTriggerConfigV2.class);
    ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) ngTriggerConfig.getSource().getSpec();
    CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
    assertThat(cronTriggerSpec.getExpression()).isEqualTo("20 4 * * *");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCreateGitlabMRComment() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTOGitlabMRComment));

    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYaml), false, false, null)
                                           .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOGitlabMRComment);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCreateBitbucketPRComment() throws Exception {
    when(ngTriggerService.createTriggerWithValidation(
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(ResponseDTO.newResponse("0", ngTriggerResponseDTOBitbucketPRComment));

    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .createV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                               yamlReq(ngTriggerYaml), false, false, null)
                                           .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOBitbucketPRComment);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalog() {
    List<TriggerCatalogType> catalogTypes = new ArrayList<>();
    catalogTypes.add(TriggerCatalogType.ECR);
    catalogTypes.add(TriggerCatalogType.ACR);
    List<TriggerCatalogItem> triggerCatalogItems = Arrays.asList(
        TriggerCatalogItem.builder().category(NGTriggerType.ARTIFACT).triggerCatalogType(catalogTypes).build());
    when(ngTriggerService.getTriggerCatalog(ACCOUNT_ID)).thenReturn(triggerCatalogItems);
    when(ngTriggerElementMapper.toCatalogDTO(triggerCatalogItems))
        .thenReturn(NGTriggerCatalogDTO.builder().catalog(triggerCatalogItems).build());

    NGTriggerCatalogDTO responseDTO = ngTriggerResource.getTriggerCatalog(ACCOUNT_ID).getData();

    assertThat(responseDTO.getCatalog().size()).isEqualTo(1);
    assertThat(responseDTO.getCatalog().get(0).getCategory()).isEqualTo(NGTriggerType.ARTIFACT);
    assertThat(responseDTO.getCatalog().get(0).getTriggerCatalogType().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalogScheduled() {
    List<TriggerCatalogType> catalogTypes = new ArrayList<>();
    catalogTypes.add(TriggerCatalogType.CRON);
    List<TriggerCatalogItem> triggerCatalogItems = Arrays.asList(
        TriggerCatalogItem.builder().category(NGTriggerType.SCHEDULED).triggerCatalogType(catalogTypes).build());
    when(ngTriggerService.getTriggerCatalog(ACCOUNT_ID)).thenReturn(triggerCatalogItems);
    when(ngTriggerElementMapper.toCatalogDTO(triggerCatalogItems))
        .thenReturn(NGTriggerCatalogDTO.builder().catalog(triggerCatalogItems).build());

    NGTriggerCatalogDTO responseDTO = ngTriggerResource.getTriggerCatalog(ACCOUNT_ID).getData();

    assertThat(responseDTO.getCatalog().size()).isEqualTo(1);
    assertThat(responseDTO.getCatalog().get(0).getCategory()).isEqualTo(NGTriggerType.SCHEDULED);
    assertThat(responseDTO.getCatalog().get(0).getTriggerCatalogType().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalogWebhook() {
    List<TriggerCatalogType> catalogTypes = new ArrayList<>();
    catalogTypes.add(TriggerCatalogType.GITHUB);
    catalogTypes.add(TriggerCatalogType.GITLAB);
    List<TriggerCatalogItem> triggerCatalogItems = Arrays.asList(
        TriggerCatalogItem.builder().category(NGTriggerType.WEBHOOK).triggerCatalogType(catalogTypes).build());
    when(ngTriggerService.getTriggerCatalog(ACCOUNT_ID)).thenReturn(triggerCatalogItems);
    when(ngTriggerElementMapper.toCatalogDTO(triggerCatalogItems))
        .thenReturn(NGTriggerCatalogDTO.builder().catalog(triggerCatalogItems).build());

    NGTriggerCatalogDTO responseDTO = ngTriggerResource.getTriggerCatalog(ACCOUNT_ID).getData();

    assertThat(responseDTO.getCatalog().size()).isEqualTo(1);
    assertThat(responseDTO.getCatalog().get(0).getCategory()).isEqualTo(NGTriggerType.WEBHOOK);
    assertThat(responseDTO.getCatalog().get(0).getTriggerCatalogType().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testUpdateTriggerPollingStatus() {
    PollingTriggerStatusUpdateDTO statusUpdate = PollingTriggerStatusUpdateDTO.builder()
                                                     .signatures(Collections.singletonList("sig"))
                                                     .success(true)
                                                     .errorMessage("")
                                                     .lastCollectedVersions(Collections.singletonList("1.0"))
                                                     .lastCollectedTime(123L)
                                                     .build();
    when(ngTriggerService.updateTriggerPollingStatus(eq("account"), eq(statusUpdate))).thenReturn(true);
    ResponseDTO<Boolean> response = ngTriggerResource.updateTriggerPollingStatus("account", statusUpdate);
    assertThat(response.getData()).isTrue();
    verify(ngTriggerService, times(1)).updateTriggerPollingStatus(eq("account"), eq(statusUpdate));
  }
}
