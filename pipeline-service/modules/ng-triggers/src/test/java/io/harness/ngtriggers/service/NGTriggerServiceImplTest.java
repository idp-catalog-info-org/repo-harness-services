/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.TRIGGER_CUSTOM_WEBHOOK_EVENT;
import static io.harness.exception.WingsException.USER;
import static io.harness.ngtriggers.beans.source.YamlFields.PIPELINE_BRANCH_NAME;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITHUB;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHUBHAM_ANAND;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.common.NGExpressionUtils;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorResourceClient;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dto.PollingResponseDTO;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails.WebhookEventProcessingDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventStatus;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CustomMetadata;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookInfo;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpecWrapper;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.GcrSpec;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.GithubSpec;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.exceptions.yaml.InvalidTriggerYamlException;
import io.harness.ngtriggers.helpers.TriggerCatalogHelper;
import io.harness.ngtriggers.helpers.TriggerSetupUsageHelper;
import io.harness.ngtriggers.instrumentation.TriggerTelemetryHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.ngtriggers.utils.MaxMultiArtifactTriggerSourcesProvider;
import io.harness.ngtriggers.utils.TriggerReferenceHelper;
import io.harness.ngtriggers.utils.polling.PollingSubscriptionHelper;
import io.harness.ngtriggers.validations.impl.TriggerValidationHandler;
import io.harness.ngtriggers.validations.result.ValidationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.triggers.TriggerExecutorResolver;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.client.PollingResourceClient;
import io.harness.polling.contracts.GitPollingPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.service.PollingDocument;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerCustomWebhookEventRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.repositories.spring.TriggerWebhookEventRepository;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.YamlPipelineUtils;

import com.cronutils.model.time.ExecutionTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.io.Resources;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class NGTriggerServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AccessControlClient accessControlClient;
  @Mock NGSettingsClient settingsClient;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @InjectMocks NGTriggerServiceImpl ngTriggerServiceImpl;
  @Mock BuildTriggerHelper validationHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NGTriggerRepository ngTriggerRepository;
  @Mock TriggerReferenceHelper triggerReferenceHelper;
  @Mock TriggerSetupUsageHelper triggerSetupUsageHelper;
  @Mock TriggerWebhookEventRepository webhookEventQueueRepository;
  @Mock TriggerCustomWebhookEventRepository triggerCustomWebhookEventRepository;
  @Mock OutboxService outboxService;
  @Mock MaxMultiArtifactTriggerSourcesProvider maxMultiArtifactTriggerSourcesProvider;
  @Mock ExecutorService executorService;
  @Mock PollingSubscriptionHelper pollingSubscriptionHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PipelineSettingsService pipelineSettingsService;
  @Mock PipelineRetentionService pipelineRetentionService;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock TriggerExecutorResolver triggerExecutorResolver;
  @Mock TriggerTelemetryHelper triggerTelemetryHelper;
  @Mock MetricService metricService;
  @Mock ConnectorResourceClient connectorResourceClient;

  @Mock KryoSerializer kryoSerializer;
  @Mock PollingResourceClient pollingResourceClient;
  @Mock NGTriggerWebhookRegistrationService ngTriggerWebhookRegistrationService;
  @Mock TriggerValidationHandler triggerValidationHandler;
  @Mock TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock TriggerCatalogHelper triggerCatalogHelper;
  @Mock HsqsClientService hsqsClientService;
  @Mock LogBaseUrlProvider logBaseUrlProvider;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PARENT_UNIQUE_ID = "parentUniqueId";
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String WEBHOOK_ID = "webhook_id";

  private final String CONNECTOR_REF = "connector_ref";

  private final String POLLING_DOC_ID = "polling_doc_id";

  private final String X_API_KEY = "x-api-key";
  private final String API_KEY = "pat.kmpySmUISimoRrJL6NL73w.6350538bbfd93f472a549604.iCMeDe82VbCG6YnWw80h";
  ClassLoader classLoader = getClass().getClassLoader();

  String filenameGitSync = "ng-trigger-github-pr-gitsync.yaml";
  WebhookTriggerConfigV2 webhookTriggerConfig;

  String ngTriggerYamlWithGitSync;
  NGTriggerConfigV2 ngTriggerConfig;

  WebhookMetadata metadata;

  NGTriggerMetadata ngTriggerMetadata;

  NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
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
  String pipelineYamlV1;
  String multiRegionArtifactTriggerYaml;

  @Before
  public void setup() throws Exception {
    on(ngTriggerServiceImpl).set("ngTriggerElementMapper", ngTriggerElementMapper);

    when(pipelineSettingsService.isTriggerCreationWithinLimit(anyString(), anyLong())).thenReturn(true);
    when(triggerExecutorResolver.isEnforceExecutorEnabled(anyString(), anyString(), anyString())).thenReturn(false);
    doNothing()
        .when(pipelineSplitPermissionsHelper)
        .checkForPipelineRBACSplitAccessPermissions(any(), any(), any(), any(), anyBoolean(), any(), anyList());
    doNothing().when(triggerTelemetryHelper).sendTriggersCreateEvent(any(), any(), any(), anyBoolean());

    when(validationHelper.fetchPipelineYamlForTrigger(any(), any(), anyBoolean())).thenReturn(Optional.empty());
    ngTriggerYamlWithGitSync =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameGitSync)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYamlWithGitSync, NGTriggerConfigV2.class);

    webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();

    ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    pipelineYamlV1 =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline-v1.yaml")), StandardCharsets.UTF_8);
    multiRegionArtifactTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-multi-region-artifact.yaml")),
            StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTriggerFailure() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder().identifier("id").name("name").targetIdentifier("pipeline").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("not a cron").build())
                                          .build())
                                .build())
                    .build())
            .build();
    try {
      ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false);
      fail("bad cron not caught");
    } catch (Exception e) {
      assertThat(e instanceof IllegalArgumentException).isTrue();
    }
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testEmptyIdentifierTriggerFailure() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().identifier("").name("name").build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier("  ");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier("a1");
    ngTriggerEntity.setName("");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .hasMessage("Name can not be empty");

    ngTriggerEntity.setTargetIdentifier("123");
    ngTriggerEntity.setIdentifier("<+identifier>");
    ngTriggerEntity.setName("name");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .hasMessage("Trigger identifier can not contain special characters or spaces: <+identifier>");

    ngTriggerEntity.setName("<+name>");
    ngTriggerEntity.setIdentifier("<identifier");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .hasMessage("Trigger name can not contain special characters or spaces: <+name>");

    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder().identifier("identifier").name("name").targetIdentifier("").build();
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.validateTriggerConfig(
                               TriggerDetails.builder().ngTriggerEntity(ngTrigger).build(), null, false))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.validateTriggerConfig(
                               TriggerDetails.builder().ngTriggerEntity(ngTrigger).build(), null, false))
        .hasMessage("Pipeline identifier can not be empty");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateTriggerConfigForCreate_InvalidCharsInName() {
    // Setup a trigger with invalid character in name
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("valid_id")
                                          .name("Invalid@Name") // @ is not allowed
                                          .targetIdentifier("pipeline")
                                          .build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    // Create a spy of ngTriggerServiceImpl
    NGTriggerServiceImpl serviceSpy = spy(ngTriggerServiceImpl);

    // Mock validateTriggerConfig to do nothing
    doNothing().when(serviceSpy).validateTriggerConfig(any(TriggerDetails.class), any(), anyBoolean());

    // Exception should be thrown for invalid name
    assertThatThrownBy(() -> serviceSpy.validateTriggerConfigForCreate(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Trigger name must start with a letter, number, underscore, hyphen, or dot and can only "
            + "contain alphanumeric, dot, hyphen, space and underscore characters");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateTriggerConfigForCreate_ValidCharsInNameAndIdentifier() {
    // Setup a trigger with valid characters in name and identifier
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("valid_trigger_id12")
                                          .name("Valid Trigger Name 1.2-3")
                                          .targetIdentifier("pipeline")
                                          .build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    // Create a spy of ngTriggerServiceImpl
    NGTriggerServiceImpl serviceSpy = spy(ngTriggerServiceImpl);

    // Mock validateTriggerConfig to do nothing
    doNothing().when(serviceSpy).validateTriggerConfig(any(TriggerDetails.class), any(), anyBoolean());

    // No exception should be thrown for valid names and identifiers
    assertThatCode(() -> serviceSpy.validateTriggerConfigForCreate(triggerDetails, null, false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testValidateTriggerConfigForCreate_InvalidCharsInIdentifier() {
    // Setup a trigger with invalid character in identifier
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("1invalid_id") // Starting with a number is not allowed
                                          .name("Valid Name")
                                          .targetIdentifier("pipeline")
                                          .build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    // Create a spy of ngTriggerServiceImpl
    NGTriggerServiceImpl serviceSpy = spy(ngTriggerServiceImpl);

    // Mock validateTriggerConfig to do nothing
    doNothing().when(serviceSpy).validateTriggerConfig(any(TriggerDetails.class), any(), anyBoolean());

    // Exception should be thrown for invalid identifier
    assertThatThrownBy(() -> serviceSpy.validateTriggerConfigForCreate(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Trigger identifier must start with a letter or underscore and can only contain "
            + "alphanumeric and underscore characters");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTrigger() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder().identifier("id").name("name").targetIdentifier("pipeline").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("20 4 * * *").build())
                                          .build())
                                .build())
                    .build())
            .build();

    ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithValidQuartz() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder().identifier("id").name("name").targetIdentifier("pipeline").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(
                                ScheduledTriggerConfig.builder()
                                    .type("Cron")
                                    .spec(
                                        CronTriggerSpec.builder().type("QUARTZ").expression("0 0 3 ? * 3#2 *").build())
                                    .build())
                            .build())
                    .build())
            .build();

    ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithInvalidQuartz() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder().identifier("id").name("name").targetIdentifier("pipeline").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(ScheduledTriggerConfig.builder()
                                      .type("Cron")
                                      .spec(CronTriggerSpec.builder().type("QUARTZ").expression("0 3 ? * 3#2").build())
                                      .build())
                            .build())
                    .build())
            .build();
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cron expression contains 5 parts but we expect one of [6, 7]");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithFiringTimeDifferenceLessThan5Minutes() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder().identifier("id").name("name").targetIdentifier("pipeline").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(ScheduledTriggerConfig.builder()
                                      .type("Cron")
                                      .spec(CronTriggerSpec.builder().type("UNIX").expression("0/1 0 * * *").build())
                                      .build())
                            .build())
                    .build())
            .build();

    // Mock ExecutionTime to return predetermined execution times
    ExecutionTime executionTime = mock(ExecutionTime.class);
    Mockito.mockStatic(ExecutionTime.class);
    when(ExecutionTime.forCron(any())).thenReturn(executionTime);
    ZonedDateTime firstExecutionTime = ZonedDateTime.of(2022, 1, 1, 0, 40, 0, 0, ZoneId.systemDefault());
    ZonedDateTime secondExecutionTime = firstExecutionTime.plusMinutes(1);
    when(executionTime.nextExecution(any(ZonedDateTime.class)))
        .thenReturn(Optional.of(firstExecutionTime), Optional.of(secondExecutionTime));

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Cron interval must be greater than or equal to 5 minutes. The next two execution times when this "
            + "trigger is suppose to fire are " + firstExecutionTime.toLocalTime().toString() + " and "
            + secondExecutionTime.toLocalTime().toString()
            + " which do not have a difference of 5 minutes between them.");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithInvalidUnix() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder().identifier("id").name("name").targetIdentifier("pipeline").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(ScheduledTriggerConfig.builder()
                                      .type("Cron")
                                      .spec(CronTriggerSpec.builder().type("UNIX").expression("0 3 * * 3 *").build())
                                      .build())
                            .build())
                    .build())
            .build();
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cron expression contains 6 parts but we expect one of [5]");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testValidateTriggerConfig() {
    when(maxMultiArtifactTriggerSourcesProvider.get()).thenReturn(10);
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .metadata(ngTriggerMetadata)
                                          .yaml("yaml")
                                          .pollInterval("1m")
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .source(NGTriggerSourceV2.builder()
                                               .type(NGTriggerType.WEBHOOK)
                                               .spec(WebhookTriggerConfigV2.builder()
                                                         .type(GITHUB)
                                                         .spec(GithubSpec.builder().build())
                                                         .build())
                                               .build())
                                   .inputSetRefs(ParameterField.createValueField(Collections.emptyList()))
                                   .pipelineBranchName("pipelineBranchName")
                                   .build())
            .build();
    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    when(ngTriggerElementMapper.shouldGitWebhookPolling(any(), any(), any(), any(), anyBoolean())).thenReturn(true);

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Poll Interval should be between 2 and 60 minutes");

    // polling interval is null
    ngTriggerEntity.setPollInterval(null);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .doesNotThrowAnyException();

    NGTriggerConfigV2 artifactTriggerConfig = NGTriggerConfigV2.builder()
                                                  .source(NGTriggerSourceV2.builder()
                                                              .type(NGTriggerType.ARTIFACT)
                                                              .spec(ArtifactTriggerConfig.builder().build())
                                                              .build())
                                                  .build();
    triggerDetails.setNgTriggerConfigV2(artifactTriggerConfig);
    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);

    // trigger type artifact
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. artifactRef can not be blank/missing. ");

    ngTriggerEntity.setWithServiceV2(true);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .doesNotThrowAnyException();

    // trigger type manifest
    ngTriggerEntity.setWithServiceV2(false);
    NGTriggerConfigV2 manifestTriggerConfig = NGTriggerConfigV2.builder()
                                                  .source(NGTriggerSourceV2.builder()
                                                              .type(NGTriggerType.MANIFEST)
                                                              .spec(ManifestTriggerConfig.builder().build())
                                                              .build())
                                                  .build();
    triggerDetails.setNgTriggerConfigV2(manifestTriggerConfig);
    ngTriggerEntity.setType(NGTriggerType.MANIFEST);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. manifestRef can not be blank/missing. ");

    ngTriggerEntity.setWithServiceV2(true);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .doesNotThrowAnyException();

    // trigger type MULTI_REGION_ARTIFACT
    ngTriggerEntity.setWithServiceV2(false);
    NGTriggerConfigV2 multiRegionArtifactTriggerConfig =
        NGTriggerConfigV2.builder()
            .source(NGTriggerSourceV2.builder()
                        .type(NGTriggerType.MULTI_REGION_ARTIFACT)
                        .spec(MultiRegionArtifactTriggerConfig.builder().build())
                        .build())
            .build();
    triggerDetails.setNgTriggerConfigV2(multiRegionArtifactTriggerConfig);
    ngTriggerEntity.setType(NGTriggerType.MULTI_REGION_ARTIFACT);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Multi-Artifact triggers are only supported with Service V2.\n"
            + "Multi-region Artifact trigger source type must have a valid artifact source type value.\n"
            + "Multi-region Artifact trigger sources list must have at least one element.\n");

    List<ArtifactTypeSpecWrapper> artifactTypeSpecWrapperList = new ArrayList<>();
    ArtifactTypeSpecWrapper artifactTypeSpecWrapper = new ArtifactTypeSpecWrapper();
    artifactTypeSpecWrapper.setSpec(DockerRegistrySpec.builder().build());
    artifactTypeSpecWrapperList.add(artifactTypeSpecWrapper);
    ArtifactTypeSpecWrapper artifactTypeSpecWrapper2 = new ArtifactTypeSpecWrapper();
    artifactTypeSpecWrapper2.setSpec(GcrSpec.builder().build());
    artifactTypeSpecWrapperList.add(artifactTypeSpecWrapper2);
    multiRegionArtifactTriggerConfig.setSource(NGTriggerSourceV2.builder()
                                                   .type(NGTriggerType.MULTI_REGION_ARTIFACT)
                                                   .spec(MultiRegionArtifactTriggerConfig.builder()
                                                             .sources(artifactTypeSpecWrapperList)
                                                             .type(ArtifactType.GCR)
                                                             .build())
                                                   .build());
    triggerDetails.setNgTriggerConfigV2(multiRegionArtifactTriggerConfig);

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Multi-Artifact triggers are only supported with Service V2.\n"
            + "Multi-region Artifact sources must all be of type DockerRegistry.\n");

    artifactTypeSpecWrapperList = new ArrayList<>();
    artifactTypeSpecWrapper = new ArtifactTypeSpecWrapper();
    artifactTypeSpecWrapper.setSpec(DockerRegistrySpec.builder().build());
    artifactTypeSpecWrapperList.add(artifactTypeSpecWrapper);
    multiRegionArtifactTriggerConfig.setSource(NGTriggerSourceV2.builder()
                                                   .type(NGTriggerType.MULTI_REGION_ARTIFACT)
                                                   .spec(MultiRegionArtifactTriggerConfig.builder()
                                                             .sources(artifactTypeSpecWrapperList)
                                                             .type(ArtifactType.GCR)
                                                             .build())
                                                   .build());
    triggerDetails.setNgTriggerConfigV2(multiRegionArtifactTriggerConfig);
    ngTriggerEntity.setWithServiceV2(true);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void testValidateManifestTriggerConfigRejectsInvalidEventConditionKey() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("identifier")
                                          .name("name")
                                          .targetIdentifier("pipeline")
                                          .type(NGTriggerType.MANIFEST)
                                          .withServiceV2(true)
                                          .build();
    TriggerEventDataCondition condition = TriggerEventDataCondition.builder().key("tagRegex").build();
    ManifestTriggerConfig manifestTriggerConfig =
        ManifestTriggerConfig.builder()
            .spec(HelmManifestSpec.builder().eventConditions(Collections.singletonList(condition)).build())
            .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder().type(NGTriggerType.MANIFEST).spec(manifestTriggerConfig).build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid eventConditions key 'tagRegex'. Valid keys are [build, version].");
  }

  @Test
  @Owner(developers = SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void testValidateArtifactTriggerConfigRejectsInvalidEventConditionKey() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("identifier")
                                          .name("name")
                                          .targetIdentifier("pipeline")
                                          .type(NGTriggerType.ARTIFACT)
                                          .withServiceV2(true)
                                          .build();
    TriggerEventDataCondition condition = TriggerEventDataCondition.builder().key("tagRegex").build();
    ArtifactTriggerConfig artifactTriggerConfig =
        ArtifactTriggerConfig.builder()
            .spec(EcrSpec.builder().eventConditions(Collections.singletonList(condition)).build())
            .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder().type(NGTriggerType.ARTIFACT).spec(artifactTriggerConfig).build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid eventConditions key 'tagRegex'. Valid keys are [build, version].");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFetchTriggerEventHistory() {
    when(triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(ACCOUNT_ID, "eventId"))
        .thenReturn(Collections.emptyList());
    assertThatThrownBy(() -> ngTriggerServiceImpl.fetchTriggerEventHistory(ACCOUNT_ID, "eventId"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger event history doesn't exist for event with eventId eventId");
    List<TriggerEventHistory> triggerEventHistories = new ArrayList<>();
    TriggerEventHistory triggerEventHistory1 = TriggerEventHistory.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .triggerIdentifier(IDENTIFIER)
                                                   .targetIdentifier(PIPELINE_IDENTIFIER)
                                                   .targetExecutionSummary(TargetExecutionSummary.builder()
                                                                               .planExecutionId("planExecutionId2")
                                                                               .runtimeInput("runtimeInput2")
                                                                               .build())
                                                   .build();
    TriggerEventHistory triggerEventHistory2 = TriggerEventHistory.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .parentUniqueId(PARENT_UNIQUE_ID)
                                                   .triggerIdentifier(IDENTIFIER)
                                                   .targetIdentifier(PIPELINE_IDENTIFIER)
                                                   .targetExecutionSummary(TargetExecutionSummary.builder()
                                                                               .planExecutionId("planExecutionId")
                                                                               .runtimeInput("runtimeInput")
                                                                               .build())
                                                   .build();
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .thenReturn(scopeInfoFor(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PARENT_UNIQUE_ID));
    triggerEventHistories.add(triggerEventHistory2);
    triggerEventHistories.add(triggerEventHistory1);
    WebhookEventProcessingDetailsBuilder builder =
        WebhookEventProcessingDetails.builder()
            .eventId("eventId")
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .triggerIdentifier(IDENTIFIER)
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .pipelineExecutionId("planExecutionId")
            .runtimeInput("runtimeInput")
            .eventFound(true)
            .warningMsg("There are multiple trigger events generated from this eventId. This response contains only "
                + "one of them.");
    when(triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(ACCOUNT_ID, "eventId"))
        .thenReturn(triggerEventHistories);
    assertThat(ngTriggerServiceImpl.fetchTriggerEventHistory(ACCOUNT_ID, "eventId")).isEqualTo(builder.build());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testListEnabledTriggersForCurrentProject() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().build();
    when(ngTriggerRepository.findByAccountIdAndEnabled(ACCOUNT_ID, true)).thenReturn(Optional.empty());
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, null, null))
        .isEqualTo(Collections.emptyList());

    when(ngTriggerRepository.findByAccountIdAndEnabled(ACCOUNT_ID, true))
        .thenReturn(Optional.of(Collections.singletonList(ngTriggerEntity)));
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, null, null))
        .isEqualTo(Collections.singletonList(ngTriggerEntity));

    NGTriggerEntity ngTriggerEntity2 = NGTriggerEntity.builder().orgIdentifier(ORG_IDENTIFIER).build();
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndEnabled(ACCOUNT_ID, ORG_IDENTIFIER, true))
        .thenReturn(Optional.of(Collections.singletonList(ngTriggerEntity2)));
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .isEqualTo(Collections.singletonList(ngTriggerEntity2));

    NGTriggerEntity ngTriggerEntity3 =
        NGTriggerEntity.builder().orgIdentifier(ORG_IDENTIFIER).projectIdentifier(PROJ_IDENTIFIER).build();
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnabled(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, true))
        .thenReturn(Optional.of(Collections.singletonList(ngTriggerEntity2)));
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo(Collections.singletonList(ngTriggerEntity2));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testList() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .type(NGTriggerType.WEBHOOK)
            .build();
    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    Page<NGTriggerEntity> ngTriggerEntityPage = ngTriggerServiceImpl.list(any(), any());
    assertThat(ngTriggerEntityPage.getContent().get(0)).isEqualTo(ngTrigger);
    assertThat(ngTriggerEntityPage.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindTriggersForCustomWebhook() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
                                    .accountId(ACCOUNT_ID)
                                    .enabled(Boolean.TRUE)
                                    .deleted(Boolean.FALSE)
                                    .identifier(IDENTIFIER)
                                    .projectIdentifier(PROJ_IDENTIFIER)
                                    .targetIdentifier(PIPELINE_IDENTIFIER)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .type(NGTriggerType.WEBHOOK)
                                    .build();
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .build();
    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    assertThat(ngTriggerServiceImpl.findTriggersForCustomWehbook(triggerWebhookEvent, true))
        .isEqualTo(Collections.singletonList(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindTriggersForCustomWebhookViaCustomWebhookToken() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
                                    .accountId(ACCOUNT_ID)
                                    .enabled(Boolean.TRUE)
                                    .deleted(Boolean.FALSE)
                                    .identifier(IDENTIFIER)
                                    .projectIdentifier(PROJ_IDENTIFIER)
                                    .targetIdentifier(PIPELINE_IDENTIFIER)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .type(NGTriggerType.WEBHOOK)
                                    .build();
    when(ngTriggerRepository.findByCustomWebhookToken(any())).thenReturn(Optional.ofNullable(ngTrigger));
    assertThat(ngTriggerServiceImpl.findTriggersForCustomWebhookViaCustomWebhookToken("webhook"))
        .isEqualTo(Optional.ofNullable(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindTriggersForWehbookBySourceRepoType() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().type(String.valueOf(GITHUB)).build())
                          .build())
            .build();
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .sourceRepoType("Github")
                                                  .build();
    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    assertThat(ngTriggerServiceImpl.findTriggersForWehbookBySourceRepoType(triggerWebhookEvent, true))
        .isEqualTo(Collections.singletonList(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindBuildTriggersByAccountIdAndSignature() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
                                    .accountId(ACCOUNT_ID)
                                    .enabled(Boolean.TRUE)
                                    .deleted(Boolean.FALSE)
                                    .identifier(IDENTIFIER)
                                    .projectIdentifier(PROJ_IDENTIFIER)
                                    .targetIdentifier(PIPELINE_IDENTIFIER)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .type(NGTriggerType.ARTIFACT)
                                    .build();

    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    assertThat(ngTriggerServiceImpl.findBuildTriggersByAccountIdAndSignature(
                   ACCOUNT_ID, Collections.singletonList("signature")))
        .isEqualTo(Collections.singletonList(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testValidateTrigger() {
    doThrow(new InvalidRequestException("message"))
        .when(triggerValidationHandler)
        .applyValidations(any(), eq(null), anyBoolean());
    when(ngTriggerRepository.updateValidationStatus(any(), any()))
        .thenReturn(NGTriggerEntity.builder()
                        .triggerStatus(TriggerStatus.builder()
                                           .validationStatus(ValidationStatus.builder()
                                                                 .statusResult(StatusResult.FAILED)
                                                                 .detailedMessage("message")
                                                                 .build())
                                           .build())
                        .build());
    ValidationStatus validationStatus =
        ngTriggerServiceImpl.validateTrigger(NGTriggerEntity.builder().build(), null, false)
            .getTriggerStatus()
            .getValidationStatus();
    assertThat(validationStatus.getDetailedMessage()).isEqualTo("message");
    assertThat(validationStatus.getStatusResult()).isEqualTo(StatusResult.FAILED);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteTriggerWebhookEvent() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder().build();
    doNothing().when(webhookEventQueueRepository).delete(triggerWebhookEvent);
    ngTriggerServiceImpl.deleteTriggerWebhookEvent(triggerWebhookEvent);
    verify(webhookEventQueueRepository, times(1)).delete(triggerWebhookEvent);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testDeleteTriggerCustomWebhookEvent() {
    TriggerCustomWebhookEvent triggerCustomWebhookEvent = TriggerCustomWebhookEvent.builder().build();
    doNothing().when(triggerCustomWebhookEventRepository).delete(triggerCustomWebhookEvent);
    ngTriggerServiceImpl.deleteTriggerCustomWebhookEvent(triggerCustomWebhookEvent);
    verify(triggerCustomWebhookEventRepository, times(1)).delete(triggerCustomWebhookEvent);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testEnqueueTriggerCustomWebhookEventSuccess() {
    String accountId = "account123";
    String eventId = "event123";
    TriggerCustomWebhookEvent event = TriggerCustomWebhookEvent.builder().uuid(eventId).accountId(accountId).build();
    when(triggerCustomWebhookEventRepository.save(event)).thenReturn(event);
    TriggerCustomWebhookEvent result = ngTriggerServiceImpl.enqueueTriggerCustomWebhookEvent(event);
    verify(hsqsClientService)
        .enqueue(argThat(request
            -> request.getTopic().equals("pms" + TRIGGER_CUSTOM_WEBHOOK_EVENT)
                && request.getSubTopic().equals(accountId)
                && request.getProducerName().equals("pms" + TRIGGER_CUSTOM_WEBHOOK_EVENT)
                && request.getPayload().contains(eventId) && request.getPayload().contains(accountId)));
    assertThat(result).isEqualTo(event);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testEnqueueTriggerCustomWebhookEventEnqueueFailure() {
    String accountId = "account123";
    String eventId = "event123";

    TriggerCustomWebhookEvent event = TriggerCustomWebhookEvent.builder().uuid(eventId).accountId(accountId).build();

    when(triggerCustomWebhookEventRepository.save(event)).thenReturn(event);
    doThrow(new RuntimeException("Queue error")).when(hsqsClientService).enqueue(any());

    assertThatThrownBy(() -> ngTriggerServiceImpl.enqueueTriggerCustomWebhookEvent(event))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Exception while queueing webhook request");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateTriggerWebhookEvent() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .sourceRepoType("Github")
                                                  .build();
    when(webhookEventQueueRepository.update(any(), any())).thenReturn(triggerWebhookEvent);
    assertThat(ngTriggerServiceImpl.updateTriggerWebhookEvent(triggerWebhookEvent)).isEqualTo(triggerWebhookEvent);

    // Exception
    when(webhookEventQueueRepository.update(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.updateTriggerWebhookEvent(triggerWebhookEvent))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateTriggerCustomWebhookEvent() {
    TriggerCustomWebhookEvent triggerCustomWebhookEvent = TriggerCustomWebhookEvent.builder()
                                                              .triggerIdentifier(IDENTIFIER)
                                                              .accountId(ACCOUNT_ID)
                                                              .orgIdentifier(ORG_IDENTIFIER)
                                                              .projectIdentifier(PROJ_IDENTIFIER)
                                                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                              .payload("payload")
                                                              .sourceRepoType("Github")
                                                              .uuid("uuid")
                                                              .build();
    when(triggerCustomWebhookEventRepository.update(any(), eq(1), eq(TriggerCustomWebhookEventStatus.QUEUED.name())))
        .thenReturn(triggerCustomWebhookEvent);
    assertThat(ngTriggerServiceImpl.updateTriggerCustomWebhookEvent(
                   "uuid", 1, TriggerCustomWebhookEventStatus.QUEUED.name(), null))
        .isEqualTo(triggerCustomWebhookEvent);
    when(triggerCustomWebhookEventRepository.update(any(), any(), any())).thenReturn(null);
    assertNull(ngTriggerServiceImpl.updateTriggerCustomWebhookEvent(
        "uuid", 1, TriggerCustomWebhookEventStatus.QUEUED.name(), null));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testAddEventToQueue() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .sourceRepoType("Github")
                                                  .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PARENT_UNIQUE_ID)
                              .build();
    when(scopeResolutionHelper.getScopeInfoOptional(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(Optional.of(scopeInfo));
    when(webhookEventQueueRepository.save(any())).thenReturn(triggerWebhookEvent);
    assertThat(ngTriggerServiceImpl.addEventToQueue(triggerWebhookEvent)).isEqualTo(triggerWebhookEvent);

    // exception
    when(webhookEventQueueRepository.save(any())).thenThrow(new InvalidRequestException("message"));
    assertThatThrownBy(() -> ngTriggerServiceImpl.addEventToQueue(triggerWebhookEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Webhook event could not be received");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDelete() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .type(NGTriggerType.WEBHOOK)
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));

    Boolean res = ngTriggerServiceImpl.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, null, false);
    assertTrue(res);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteException() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.unacknowledged());

    ngTriggerServiceImpl.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, null, false);
  }
  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDeleteWebhookPolling() throws IOException {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();

    PollingItem pollingItem = createPollingItem(ngTrigger);
    Call<Boolean> call = mock(Call.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);
    byte[] bytes = {70};

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(pollingSubscriptionHelper.generatePollingItems(eq(ngTrigger), anyBoolean(), eq(null), anyBoolean()))
        .thenReturn(Collections.singletonList(pollingItem));
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(kryoSerializer.asBytes(any(PollingItem.class))).thenReturn(bytes);

    Boolean res = ngTriggerServiceImpl.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null, null, false);
    assertTrue(res);
  }

  private PollingItem createPollingItem(NGTriggerEntity ngTrigger) {
    return PollingItem.newBuilder()
        .setPollingDocId(POLLING_DOC_ID)
        .setPollingPayloadData(
            PollingPayloadData.newBuilder()
                .setConnectorRef(CONNECTOR_REF)
                .setGitPollPayload(GitPollingPayload.newBuilder()
                                       .setWebhookId(ngTrigger.getTriggerStatus().getWebhookInfo().getWebhookId())
                                       .setPollInterval(NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(
                                           ngTrigger.getPollInterval()))
                                       .buildPartial())
                .build())
        .build();
  }

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteSetupUsage() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.ofNullable(ngTrigger));
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    ngTriggerServiceImpl.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, 2L, null, false);
    verify(triggerSetupUsageHelper, times(1)).deleteExistingSetupUsages(ngTrigger, null, false);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalog() {
    when(triggerCatalogHelper.getTriggerTypeToCategoryMapping(ACCOUNT_ID))
        .thenReturn(
            Arrays.asList(TriggerCatalogItem.builder()
                              .category(NGTriggerType.ARTIFACT)
                              .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.ACR)))
                              .build(),
                TriggerCatalogItem.builder()
                    .category(NGTriggerType.WEBHOOK)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.GITHUB)))
                    .build(),
                TriggerCatalogItem.builder()
                    .category(NGTriggerType.SCHEDULED)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.CRON)))
                    .build(),
                TriggerCatalogItem.builder()
                    .category(NGTriggerType.MANIFEST)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.HELM_CHART)))
                    .build()));
    List<TriggerCatalogItem> lst = ngTriggerServiceImpl.getTriggerCatalog(ACCOUNT_ID);
    assertThat(lst).isNotNull();
    assertThat(lst.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckAuthorizationSuccessWhenXApiKeyIsPresent() {
    List<HeaderConfig> headerConfigs = Collections.singletonList(
        HeaderConfig.builder().key(X_API_KEY).values(Collections.singletonList(API_KEY)).build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatCode(()
                       -> ngTriggerServiceImpl.checkAuthorization(
                           ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCheckAuthorizationWhenXApiKeyIsAbsent() throws IOException {
    List<HeaderConfig> headerConfigs = Collections.emptyList();
    Call<ResponseDTO<SettingValueResponseDTO>> settingValueResponseDTOCall = mock(Call.class);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(settingsClient.getSetting(any(), any(), any(), any())).thenReturn(settingValueResponseDTOCall);
    when(settingValueResponseDTOCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.checkAuthorization(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, headerConfigs))
        .isInstanceOf(InvalidRequestException.class);

    settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(settingValueResponseDTOCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    assertThatCode(()
                       -> ngTriggerServiceImpl.checkAuthorization(
                           ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFetchTriggerEntity() {
    NGTriggerConfigV2 ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                                              .identifier(IDENTIFIER)
                                              .orgIdentifier(ORG_IDENTIFIER)
                                              .projectIdentifier(PROJ_IDENTIFIER)
                                              .inputYaml("inputYaml")
                                              .build();

    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();
    NGTriggerEntity ngTrigger2 =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .yaml("yaml")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();
    when(ngTriggerElementMapper.toTriggerConfigV2(anyString())).thenReturn(ngTriggerConfigV2);
    when(ngTriggerElementMapper.toTriggerEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, "yaml", true))
        .thenReturn(ngTrigger2);
    doNothing().when(ngTriggerElementMapper).copyEntityFieldsOutsideOfYml(ngTrigger, ngTrigger2, null, false);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             any(), any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(ngTrigger));
    assertThat(ngTriggerServiceImpl.fetchTriggerEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                   IDENTIFIER, "yaml", true, null, false))
        .isEqualTo(TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).ngTriggerEntity(ngTrigger2).build());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFetchExecutionSummaryV2() throws IOException {
    Call<ResponseDTO<Object>> call = mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2("planExecutionId", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse("object")));
    assertThat(
        ngTriggerServiceImpl.fetchExecutionSummaryV2("planExecutionId", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo("object");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckAuthorizationFailureWhenXApiKeyIsPresent() {
    List<HeaderConfig> headerConfigs = Collections.singletonList(
        HeaderConfig.builder().key(X_API_KEY).values(Collections.singletonList(API_KEY)).build());
    doThrow(new AccessDeniedException("Error msg", USER))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.checkAuthorization(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCreate() {
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.SPG_DISABLE_CUSTOM_WEBHOOK_V3_URL)))
        .thenReturn(true);
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().git(GitMetadata.builder().build()).build())
                          .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .triggerStatus(TriggerStatus.builder()
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PARENT_UNIQUE_ID)
                              .build();
    when(scopeResolutionHelper.getScopeInfoOptional(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(Optional.of(scopeInfo));
    doReturn(ngTriggerEntity).when(ngTriggerRepository).save(any());
    when(ngTriggerElementMapper.toTriggerConfigV2(eq(ngTriggerEntity), any(), anyBoolean())).thenReturn(null);
    doThrow(new InvalidRequestException("message"))
        .when(triggerReferenceHelper)
        .getReferences(any(), any(), any(), anyBoolean());

    // when we get exception while publishing setupUsages
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(ngTriggerWebhookRegistrationService.registerWebhook(any(), any(), anyBoolean()))
        .thenReturn(WebhookRegistrationStatusData.builder()
                        .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                           .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                           .build())
                        .build());
    when(ngTriggerRepository.update(any(), any())).thenReturn(ngTriggerEntity);
    when(ngTriggerRepository.updateValidationStatus(any(), any())).thenReturn(ngTriggerEntity);
    ngTriggerServiceImpl.create(ngTriggerEntity, null, false);
    verify(scopeResolutionHelper, times(1)).getScopeInfoOptional(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    verify(ngTriggerWebhookRegistrationService, times(1)).registerWebhook(any(), any(), anyBoolean());
    verify(triggerSetupUsageHelper, times(0)).publishSetupUsageEvent(any(), any(), any(), anyBoolean());

    // when updateWebhookRegistrationStatus throws error
    when(ngTriggerRepository.update(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.create(ngTriggerEntity, null, false))
        .isInstanceOf(InvalidRequestException.class);

    // Exception on saving duplicate entity
    doThrow(new DuplicateKeyException("message")).when(ngTriggerRepository).save(any());
    assertThatThrownBy(() -> ngTriggerServiceImpl.create(ngTriggerEntity, null, false))
        .isInstanceOf(DuplicateFieldException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdate() {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().git(GitMetadata.builder().build()).build())
                          .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .triggerStatus(TriggerStatus.builder()
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    NGTriggerEntity oldTriggerEntity = ngTriggerEntity;
    oldTriggerEntity.setName("name2");

    // when db update returns null
    when(ngTriggerRepository.update(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.update(ngTriggerEntity, oldTriggerEntity, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NGTrigger [" + IDENTIFIER + "] couldn't be updated or doesn't exist");

    // when getReferences throws exception
    when(ngTriggerRepository.update(any(), any())).thenReturn(ngTriggerEntity);
    when(ngTriggerElementMapper.toTriggerConfigV2(eq(ngTriggerEntity), any(), anyBoolean()))
        .thenReturn(NGTriggerConfigV2.builder().build());
    doThrow(new InvalidRequestException("message"))
        .when(triggerReferenceHelper)
        .getReferences(any(), any(), any(), anyBoolean());
    ValidationResult validationResult = ValidationResult.builder().success(true).build();
    TriggerDetails triggerDetails = TriggerDetails.builder().build();
    when(ngTriggerElementMapper.toTriggerDetails(any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(triggerDetails);
    when(ngTriggerElementMapper.toTriggerDetails(any(NGTriggerEntity.class), any(), anyBoolean()))
        .thenReturn(triggerDetails);
    when(triggerValidationHandler.applyValidations(any(), eq(null), anyBoolean())).thenReturn(validationResult);
    ngTriggerServiceImpl.update(ngTriggerEntity, oldTriggerEntity, null, false);
    verify(triggerSetupUsageHelper, times(0)).deleteExistingSetupUsages(any(), any(), anyBoolean());

    List<EntityDetailProtoDTO> referredEntities = Collections.singletonList(EntityDetailProtoDTO.newBuilder().build());
    doReturn(referredEntities).when(triggerReferenceHelper).getReferences(any(), any(), any(), anyBoolean());
    doNothing().when(triggerSetupUsageHelper).publishSetupUsageEvent(ngTriggerEntity, referredEntities, null, false);
    assertThat(ngTriggerServiceImpl.update(ngTriggerEntity, oldTriggerEntity, null, false)).isEqualTo(ngTriggerEntity);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteAllForPipeline() throws IOException {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .parentUniqueId(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().git(GitMetadata.builder().build()).build())
                          .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .pollInterval("0")
            .triggerStatus(TriggerStatus.builder()
                               .webhookInfo(WebhookInfo.builder().webhookId("webhookId").build())
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    List<NGTriggerEntity> ngTriggerEntities = Collections.singletonList(ngTriggerEntity);
    when(ngTriggerRepository.findByParentUniqueIdAndTargetIdentifier(eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER)))
        .thenReturn(Optional.of(ngTriggerEntities));
    PollingItem pollingItem = createPollingItem(ngTriggerEntity);
    Call<Boolean> call = mock(Call.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTriggerEntity);
    byte[] bytes = {70};

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    when(ngTriggerRepository.findByParentUniqueIdAndTargetIdentifierAndIdentifier(
             eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(pollingSubscriptionHelper.generatePollingItems(eq(ngTriggerEntity), anyBoolean(), eq(null), anyBoolean()))
        .thenReturn(List.of(pollingItem));
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(kryoSerializer.asBytes(any(PollingItem.class))).thenReturn(bytes);

    assertThat(ngTriggerServiceImpl.deleteAllForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo(true);

    // Exception occurs while deleting
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.unacknowledged());
    assertThat(ngTriggerServiceImpl.deleteAllForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo(false);

    // Exception occured while getting ngTriggerEntity from db
    doThrow(new InvalidRequestException("message"))
        .when(ngTriggerRepository)
        .findByParentUniqueIdAndTargetIdentifier(any(), any());
    assertThat(ngTriggerServiceImpl.deleteAllForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo(false);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testSubscribePolling() throws IOException {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .enabled(false)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .webhookId("webhookId")
            .pollInterval("1")
            .metadata(
                NGTriggerMetadata.builder()
                    .buildMetadata(BuildMetadata.builder()
                                       .pollingConfig(PollingConfig.builder().pollingDocId("pollingDocId").build())
                                       .build())
                    .webhook(WebhookMetadata.builder()
                                 .type(GITHUB.getEntityMetadataName())
                                 .git(GitMetadata.builder().build())
                                 .build())
                    .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .triggerStatus(TriggerStatus.builder()
                               .webhookInfo(WebhookInfo.builder().webhookId("webhookId").build())
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    PollingItem pollingItem = createPollingItem(ngTriggerEntity);
    byte[] bytes = {70};
    when(pollingSubscriptionHelper.generatePollingItems(any(), anyBoolean(), eq(null), anyBoolean()))
        .thenReturn(List.of(pollingItem));
    when(kryoSerializer.asBytes(pollingItem)).thenReturn(bytes);
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.CD_GIT_WEBHOOK_POLLING))).thenReturn(true);
    Call<Boolean> call = mock(Call.class);
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(ngTriggerRepository.updateValidationStatusAndMetadata(any(), any())).thenReturn(ngTriggerEntity);
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true, false, null, false);
    verify(ngTriggerRepository, times(1)).updateValidationStatusAndMetadata(any(), any());

    // WebhookGitPollingEnabled
    when(ngTriggerElementMapper.shouldGitWebhookPolling(any(), any(), any(), any(), anyBoolean())).thenReturn(true);
    ngTriggerEntity.setEnabled(true);
    ngTriggerEntity.setPollInterval("0");
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true, false, null, false);
    verify(ngTriggerRepository, times(2)).updateValidationStatusAndMetadata(any(), any());

    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(PollingDocument.newBuilder().build());
    Call<ResponseDTO<PollingResponseDTO>> call1 = mock(Call.class);
    when(pollingResourceClient.subscribe(any(), anyBoolean())).thenReturn(call1);
    when(call1.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(PollingResponseDTO.builder().isExistingPollingDoc(false).build())));
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true, false, null, false);
    verify(ngTriggerRepository, times(3)).updateValidationStatusAndMetadata(any(), any());

    doThrow(new InvalidRequestException("message")).when(call).execute();
    assertThatThrownBy(() -> ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true, false, null, false))
        .isInstanceOf(InvalidRequestException.class);

    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(PollingDocument.newBuilder().build());
    Call<ResponseDTO<PollingResponseDTO>> call2 = mock(Call.class);
    when(pollingResourceClient.subscribe(any(), anyBoolean())).thenReturn(call2);
    when(call2.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(PollingResponseDTO.builder()
                                                                 .isExistingPollingDoc(true)
                                                                 .lastPolled(Collections.singletonList("1"))
                                                                 .build())));
    when(ngTriggerRepository.updateValidationStatus(any(), any())).thenReturn(ngTriggerEntity);
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, false, false, null, false);
    verify(ngTriggerRepository, times(5)).updateValidationStatusAndMetadata(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCreateCustomWebhookTrigger() {
    ngTriggerMetadata = NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder().custom(CustomMetadata.builder().build()).build())
                            .build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
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
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PARENT_UNIQUE_ID)
                              .build();
    when(scopeResolutionHelper.getScopeInfoOptional(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(Optional.of(scopeInfo));
    doReturn(ngTriggerEntity).when(ngTriggerRepository).save(any());
    doReturn(ngTriggerEntity).when(ngTriggerRepository).updateValidationStatus(any(), any());
    doReturn(Collections.emptyList()).when(triggerReferenceHelper).getReferences(any(), any(), any(), anyBoolean());
    doNothing().when(triggerSetupUsageHelper).publishSetupUsageEvent(any(), any(), any(), anyBoolean());
    ArgumentCaptor<NGTriggerEntity> entityAfterSave = ArgumentCaptor.forClass(NGTriggerEntity.class);
    NGTriggerEntity createdEntity = ngTriggerServiceImpl.create(ngTriggerEntity, null, false);
    verify(ngTriggerRepository, times(1)).save(entityAfterSave.capture());
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getParentUniqueId()).isEqualTo(PARENT_UNIQUE_ID);
    assertThat(entityAfterSave.getValue().getCustomWebhookToken()).isNotNull();
    ngTriggerEntity.setMetadata(NGTriggerMetadata.builder().buildMetadata(BuildMetadata.builder().build()).build());
    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    ngTriggerEntity.setCustomWebhookToken(null);
    ArgumentCaptor<NGTriggerEntity> entityAfterSave1 = ArgumentCaptor.forClass(NGTriggerEntity.class);
    NGTriggerEntity createdEntity1 = ngTriggerServiceImpl.create(ngTriggerEntity, null, false);
    verify(ngTriggerRepository, times(2)).save(entityAfterSave1.capture());
    assertThat(createdEntity1).isNotNull();
    assertThat(entityAfterSave1.getValue().getCustomWebhookToken()).isNull();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithExtraInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-extra-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithMissingInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-missing-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithNoInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-no-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithRightInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-right-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithWrongInputFormat() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml",
        "trigger-yaml-diff-trigger-wrong-input-format.yaml", "trigger-yaml-diff-expected-new-trigger-with-input.yaml",
        true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffWhenPipelineHasNoInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-no-input.yaml", "trigger-yaml-diff-trigger-extra-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-no-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffWhenTriggerForRemotePipeline() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-with-input-set.yaml",
        "trigger-yaml-diff-trigger-with-input-set.yaml", false);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateBranchName() throws IOException {
    String pipelineBranchName = "pipelineBranchName";
    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().failureCount(0).successCount(1).build();
    String filenamePipelineBranchName = "ng-trigger-pipeline-branch-name.yaml";
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .yaml(Resources.toString(
                Objects.requireNonNull(classLoader.getResource(filenamePipelineBranchName)), StandardCharsets.UTF_8))
            .build();
    Optional<List<NGTriggerEntity>> optionalNGTriggerList = Optional.of(Collections.singletonList(ngTrigger));

    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifier(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER)))
        .thenReturn(optionalNGTriggerList);

    when(ngTriggerRepository.updateTriggerYaml(any(), any(), anyBoolean())).thenReturn(triggerUpdateCount);

    ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        GitMoveOperationType.REMOTE_TO_INLINE, pipelineBranchName, null, false);

    YamlField yamlField = YamlUtils.readTree(ngTrigger.getYaml());
    YamlNode triggerNode = yamlField.getNode().getField("trigger").getNode();
    assertThat((ObjectNode) triggerNode.getCurrJsonNode().get(PIPELINE_BRANCH_NAME)).isNull();

    ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        GitMoveOperationType.INLINE_TO_REMOTE, pipelineBranchName, null, false);

    YamlField yamlField1 = YamlUtils.readTree(ngTrigger.getYaml());
    YamlNode triggerNode1 = yamlField1.getNode().getField("trigger").getNode();
    assertThat(triggerNode1.getCurrJsonNode().get(PIPELINE_BRANCH_NAME)).isEqualTo(new TextNode(pipelineBranchName));

    filenamePipelineBranchName = "pipeline-v1.yaml";
    ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .yaml(Resources.toString(
                Objects.requireNonNull(classLoader.getResource(filenamePipelineBranchName)), StandardCharsets.UTF_8))
            .build();
    optionalNGTriggerList = Optional.of(Collections.singletonList(ngTrigger));
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifier(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER)))
        .thenReturn(optionalNGTriggerList);

    when(ngTriggerRepository.updateTriggerYaml(any(), any(), anyBoolean()))
        .thenReturn(TriggerUpdateCount.builder().failureCount(0).successCount(0).build());

    assertThat(ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                   GitMoveOperationType.REMOTE_TO_INLINE, pipelineBranchName, null, false))
        .isEqualTo(TriggerUpdateCount.builder().failureCount(1L).successCount(0L).build());

    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifier(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER)))
        .thenReturn(Optional.empty());
    assertThat(ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                   GitMoveOperationType.REMOTE_TO_INLINE, pipelineBranchName, null, false))
        .isEqualTo(TriggerUpdateCount.builder().failureCount(0L).successCount(0L).build());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetInvalidFQNsInTrigger() throws IOException {
    String pipelineFilename = "ng-trigger-pipeline.yaml";
    String triggerFileName = "ng-trigger-input.yaml";
    String templateYaml = createRuntimeInputFormForTrigger(
        Resources.toString(Objects.requireNonNull(classLoader.getResource(pipelineFilename)), StandardCharsets.UTF_8));
    JsonNode node = YamlUtils
                        .readTree(Resources.toString(
                            Objects.requireNonNull(classLoader.getResource(triggerFileName)), StandardCharsets.UTF_8))
                        .getNode()
                        .getCurrJsonNode();
    ObjectNode innerMap = (ObjectNode) node.get("trigger");
    JsonNode inputYaml = innerMap.get("inputYaml");
    JsonNode pipelineNode = YamlUtils.readTree(inputYaml.asText()).getNode().getCurrJsonNode();
    String triggerPipelineYaml = YamlUtils.writeYamlString(pipelineNode);

    assertThat(ngTriggerServiceImpl.getInvalidFQNsInTrigger(templateYaml, triggerPipelineYaml, ACCOUNT_ID).size())
        .isEqualTo(0);

    String triggerExtraInputFileName = "ng-trigger-extra-input.yaml";
    node = YamlUtils
               .readTree(Resources.toString(
                   Objects.requireNonNull(classLoader.getResource(triggerExtraInputFileName)), StandardCharsets.UTF_8))
               .getNode()
               .getCurrJsonNode();
    innerMap = (ObjectNode) node.get("trigger");
    inputYaml = innerMap.get("inputYaml");
    pipelineNode = YamlUtils.readTree(inputYaml.asText()).getNode().getCurrJsonNode();
    String extraInputTriggerPipelineYaml = YamlUtils.writeYamlString(pipelineNode);

    Map<FQN, String> extraInputResult =
        ngTriggerServiceImpl.getInvalidFQNsInTrigger(templateYaml, extraInputTriggerPipelineYaml, ACCOUNT_ID);
    assertThat(extraInputResult.size()).isEqualTo(3);
    assertThat(extraInputResult.containsValue("Field either not present in pipeline or not a runtime input"))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCheckYamlDiffWithInputSetRefsAsExpression() throws IOException {
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("trigger-yaml-with-input-set-refs.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .metadata(ngTriggerMetadata)
                                          .yaml(triggerYaml)
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .inputSetRefs(ParameterField.createExpressionField(
                                       true, "<+<+trigger.payload.input_set_refs>.split(\",\")>", null, false))
                                   .build())
            .build();
    TriggerYamlDiffDTO yamlDiffResponse = ngTriggerServiceImpl.getTriggerYamlDiff(triggerDetails, null, false);
    assertThat(yamlDiffResponse.getNewYAML()).isEqualTo(triggerYaml);
  }

  private void checkTriggerYamlDiff(String filenamePipeline, String filenameTrigger, String filenameNewTrigger,
      Boolean useNullPipelineBranchName) throws IOException {
    String newTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameNewTrigger)), StandardCharsets.UTF_8);
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameTrigger)), StandardCharsets.UTF_8);
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .metadata(ngTriggerMetadata)
                                          .yaml(triggerYaml)
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .inputSetRefs(ParameterField.createValueField(Collections.emptyList()))
                                   .pipelineBranchName(useNullPipelineBranchName ? null : "pipelineBranchName")
                                   .build())
            .build();
    String pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenamePipeline)), StandardCharsets.UTF_8);
    when(validationHelper.fetchPipelineYamlForTrigger(any(), any(), anyBoolean()))
        .thenReturn(Optional.ofNullable(pipelineYaml));
    TriggerYamlDiffDTO yamlDiffResponse = ngTriggerServiceImpl.getTriggerYamlDiff(triggerDetails, null, false);
    assertThat(yamlDiffResponse.getNewYAML().replace("<+input>", "1")).isEqualTo(newTriggerYaml);
  }

  private String createRuntimeInputFormForTrigger(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\"", "");
      if (NGExpressionUtils.matchesInputSetPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCronTriggerInterval() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("0/2 * * * *").build())
                                          .build())
                                .build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails, null, false))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateTriggerStatus() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder().build();
    when(ngTriggerRepository.update(any(), any())).thenReturn(ngTrigger);
    when(ngTriggerRepository.updateValidationStatus(any(), any())).thenReturn(ngTrigger);
    assertThat(ngTriggerServiceImpl.updateTriggerStatus(ngTrigger, false, null, false)).isEqualTo(false);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testInvalidPipelineBranchName() {
    PMSPipelineResponseDTO pipelineResponse =
        PMSPipelineResponseDTO.builder().storeType(StoreType.REMOTE).yamlPipeline("yamlPipeline").build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerConfigV2(NGTriggerConfigV2.builder().build()).build();

    when(validationHelper.fetchPipelineForTrigger(eq(triggerDetails), any(), anyBoolean()))
        .thenReturn(pipelineResponse);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validatePipelineRef(triggerDetails, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("pipelineBranchName is missing or is empty.");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateTriggerWithValidationStatusWhileRuntime() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("id")
                                          .orgIdentifier("orgId")
                                          .projectIdentifier("projId")
                                          .targetIdentifier("targetId")
                                          .enabled(true)
                                          .name("name")
                                          .build();
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(ngTriggerEntity);
    doReturn(ngTriggerEntity).when(ngTriggerRepository).updateValidationStatus(any(), any());
    ArgumentCaptor<NGTriggerEntity> entityAfterUpdate = ArgumentCaptor.forClass(NGTriggerEntity.class);
    ngTriggerServiceImpl.updateTriggerWithValidationStatus(
        ngTriggerEntity, ValidationResult.builder().success(false).message("message").build(), true, null, false);
    verify(ngTriggerRepository, times(1)).updateValidationStatus(any(), entityAfterUpdate.capture());
    assertThat(entityAfterUpdate.getValue().getEnabled()).isTrue();
    assertThat(entityAfterUpdate.getValue().getTriggerStatus().getValidationStatus().getStatusResult())
        .isEqualTo(StatusResult.FAILED);
    assertThat(entityAfterUpdate.getValue().getTriggerStatus().getValidationStatus().getDetailedMessage())
        .isEqualTo("message");

    ArgumentCaptor<NGTriggerEntity> entityAfterUpdate1 = ArgumentCaptor.forClass(NGTriggerEntity.class);
    ngTriggerServiceImpl.updateTriggerWithValidationStatus(
        ngTriggerEntity, ValidationResult.builder().success(false).message("message").build(), false, null, false);
    verify(ngTriggerRepository, times(2)).updateValidationStatus(any(), entityAfterUpdate1.capture());
    assertThat(entityAfterUpdate1.getValue().getEnabled()).isFalse();
    assertThat(entityAfterUpdate1.getValue().getTriggerStatus().getValidationStatus().getStatusResult())
        .isEqualTo(StatusResult.FAILED);
    assertThat(entityAfterUpdate1.getValue().getTriggerStatus().getValidationStatus().getDetailedMessage())
        .isEqualTo("message");

    when(ngTriggerRepository.updateValidationStatus(any(), any())).thenReturn(null);
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.updateTriggerWithValidationStatus(ngTriggerEntity,
                               ValidationResult.builder().success(false).message("message").build(), true, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NGTrigger [id] couldn't be updated or doesn't exist");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecutePollingSubscriptionChangesForMultiRegionArtifactTriggerNotIsUpdate() throws Exception {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    when(pmsFeatureFlagService.isEnabled("account", FeatureName.CD_GIT_WEBHOOK_POLLING)).thenReturn(true);
    PollingItem pollingItem = PollingItem.newBuilder().setPollingDocId("id1").build();
    when(pollingSubscriptionHelper.generatePollingItems(any(), anyBoolean(), eq(null), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    when(pollingSubscriptionHelper.generateMultiArtifactPollingItemsToUnsubscribe(any(), any(), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    byte[] bytes = {70};
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    PollingDocument pollingDocument = PollingDocument.newBuilder().setPollingDocId("id1").build();
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(pollingDocument);

    ResponseDTO<PollingResponseDTO> pollingResponse =
        ResponseDTO.newResponse(PollingResponseDTO.builder().pollingResponse(bytes).build());
    Call<ResponseDTO<PollingResponseDTO>> subscribeCall = mock(Call.class);
    when(subscribeCall.execute()).thenReturn(Response.success(pollingResponse));
    when(pollingResourceClient.subscribe(any(), anyBoolean())).thenReturn(subscribeCall);

    Call<Boolean> unsubscribeCall = mock(Call.class);
    when(unsubscribeCall.execute()).thenReturn(Response.success(true));
    when(pollingResourceClient.unsubscribe(any())).thenReturn(unsubscribeCall);

    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    when(ngTriggerRepository.updateValidationStatusAndMetadata(any(), any())).thenReturn(ngTriggerEntity);

    ngTriggerServiceImpl.executePollingSubscriptionChanges(ngTriggerEntity, false, null, false);
    verify(pollingResourceClient, times(2)).subscribe(any(), anyBoolean());
    verify(pollingResourceClient, times(0)).unsubscribe(any());
    assertThat(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(0).getPollingConfig().getPollingDocId())
        .isEqualTo("id1");
    assertThat(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(1).getPollingConfig().getPollingDocId())
        .isEqualTo("id1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecutePollingSubscriptionChangesForMultiRegionArtifactTriggerIsUpdate() throws Exception {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    PollingItem pollingItem = PollingItem.newBuilder().setPollingDocId("id1").build();
    when(pollingSubscriptionHelper.generatePollingItems(any(), anyBoolean(), eq(null), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    when(pollingSubscriptionHelper.generateMultiArtifactPollingItemsToUnsubscribe(any(), any(), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    byte[] bytes = {70};
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    PollingDocument pollingDocument = PollingDocument.newBuilder().setPollingDocId("id1").build();
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(pollingDocument);

    ResponseDTO<PollingResponseDTO> pollingResponse =
        ResponseDTO.newResponse(PollingResponseDTO.builder().pollingResponse(bytes).build());
    Call<ResponseDTO<PollingResponseDTO>> subscribeCall = mock(Call.class);
    when(subscribeCall.execute()).thenReturn(Response.success(pollingResponse));
    when(pollingResourceClient.subscribe(any(), anyBoolean())).thenReturn(subscribeCall);

    Call<Boolean> unsubscribeCall = mock(Call.class);
    when(unsubscribeCall.execute()).thenReturn(Response.success(true));
    when(pollingResourceClient.unsubscribe(any())).thenReturn(unsubscribeCall);

    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    when(ngTriggerRepository.updateValidationStatusAndMetadata(any(), any())).thenReturn(ngTriggerEntity);

    ngTriggerServiceImpl.executePollingSubscriptionChanges(ngTriggerEntity, true, null, false);
    verify(pollingResourceClient, times(2)).subscribe(any(), anyBoolean());
    verify(pollingResourceClient, times(2)).unsubscribe(any());
    assertThat(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(0).getPollingConfig().getPollingDocId())
        .isEqualTo("id1");
    assertThat(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(1).getPollingConfig().getPollingDocId())
        .isEqualTo("id1");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testToggleTriggersForAccountScope() {
    String ACCOUNT_ID = "accountId";

    boolean enable = true;

    NGTriggerEntity t1 = NGTriggerEntity.builder()
                             .name("t1")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t1")
                             .projectIdentifier("project1")
                             .orgIdentifier("org1")
                             .targetIdentifier("pipeline1")
                             .build();

    NGTriggerEntity t2 = NGTriggerEntity.builder()
                             .name("t2")
                             .enabled(false)
                             .type(NGTriggerType.WEBHOOK)
                             .accountId(ACCOUNT_ID)
                             .identifier("t2")
                             .projectIdentifier("project2")
                             .orgIdentifier("org2")
                             .targetIdentifier("pipeline2")
                             .build();

    NGTriggerEntity t3 = NGTriggerEntity.builder()
                             .name("t3")
                             .enabled(false)
                             .type(NGTriggerType.MANIFEST)
                             .accountId(ACCOUNT_ID)
                             .identifier("t3")
                             .projectIdentifier("project3")
                             .orgIdentifier("org3")
                             .targetIdentifier("pipeline3")
                             .build();

    NGTriggerEntity t4 = NGTriggerEntity.builder()
                             .name("t4")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t4")
                             .projectIdentifier("project4")
                             .orgIdentifier("org4")
                             .targetIdentifier("pipeline4")
                             .build();

    NGTriggerEntity t5 = NGTriggerEntity.builder()
                             .name("t5")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t5")
                             .projectIdentifier("project5")
                             .orgIdentifier("org5")
                             .targetIdentifier("pipeline5")
                             .build();

    List<NGTriggerEntity> ngTriggerEntityList = Arrays.asList(t1, t2, t3, t4, t5);

    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().successCount(5l).failureCount(0l).build();

    Stream<NGTriggerEntity> stream = createCloseableIterator(ngTriggerEntityList.iterator()).stream();

    // Mock the behavior of ngTriggerElementMapper.updateEntityYmlWithEnabledValue
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(any(NGTriggerEntity.class));

    // Mock the behavior of ngTriggerRepository.updateTriggerEnabled
    when(ngTriggerRepository.toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean())).thenReturn(triggerUpdateCount);

    // Mock the behavior of ngTriggerRepository.findAll
    when(ngTriggerRepository.findAll(any(Criteria.class))).thenReturn(stream);

    BulkTriggersResponseDTO result = ngTriggerServiceImpl.toggleTriggers(
        enable, ACCOUNT_ID, null, null, null, null, scopeInfoFor(ACCOUNT_ID, null, null, ACCOUNT_ID));

    verify(ngTriggerElementMapper, times(ngTriggerEntityList.size())).updateEntityYmlWithEnabledValue(any());
    verify(ngTriggerRepository).toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean());

    assertEquals(result.getBulkTriggerDetailDTOList().size(), ngTriggerEntityList.size());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testToggleTriggersForOrganizationScope() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";

    boolean enable = true;

    NGTriggerEntity t1 = NGTriggerEntity.builder()
                             .name("t1")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t1")
                             .projectIdentifier("project1")
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline1")
                             .build();

    NGTriggerEntity t2 = NGTriggerEntity.builder()
                             .name("t2")
                             .enabled(false)
                             .type(NGTriggerType.WEBHOOK)
                             .accountId(ACCOUNT_ID)
                             .identifier("t2")
                             .projectIdentifier("project2")
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline2")
                             .build();

    NGTriggerEntity t3 = NGTriggerEntity.builder()
                             .name("t3")
                             .enabled(false)
                             .type(NGTriggerType.MANIFEST)
                             .accountId(ACCOUNT_ID)
                             .identifier("t3")
                             .projectIdentifier("project3")
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline3")
                             .build();

    NGTriggerEntity t4 = NGTriggerEntity.builder()
                             .name("t4")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t4")
                             .projectIdentifier("project4")
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline4")
                             .build();

    NGTriggerEntity t5 = NGTriggerEntity.builder()
                             .name("t5")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t5")
                             .projectIdentifier("project5")
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline5")
                             .build();

    List<NGTriggerEntity> ngTriggerEntityList = Arrays.asList(t1, t2, t3, t4, t5);

    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().successCount(5l).failureCount(0l).build();

    Stream<NGTriggerEntity> stream = createCloseableIterator(ngTriggerEntityList.iterator()).stream();

    // Mock the behavior of ngTriggerElementMapper.updateEntityYmlWithEnabledValue
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(any(NGTriggerEntity.class));

    // Mock the behavior of ngTriggerRepository.updateTriggerEnabled
    when(ngTriggerRepository.toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean())).thenReturn(triggerUpdateCount);

    // Mock the behavior of ngTriggerRepository.findAll
    when(ngTriggerRepository.findAll(any(Criteria.class))).thenReturn(stream);

    BulkTriggersResponseDTO result = ngTriggerServiceImpl.toggleTriggers(
        enable, ACCOUNT_ID, ORG_ID, null, null, null, scopeInfoFor(ACCOUNT_ID, ORG_ID, null, ORG_ID));

    verify(ngTriggerElementMapper, times(ngTriggerEntityList.size())).updateEntityYmlWithEnabledValue(any());
    verify(ngTriggerRepository).toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean());

    assertEquals(ngTriggerEntityList.size(), result.getBulkTriggerDetailDTOList().size());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testToggleTriggersForProjectScope() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";

    boolean enable = true;

    NGTriggerEntity t1 = NGTriggerEntity.builder()
                             .name("t1")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t1")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline1")
                             .build();

    NGTriggerEntity t2 = NGTriggerEntity.builder()
                             .name("t2")
                             .enabled(false)
                             .type(NGTriggerType.WEBHOOK)
                             .accountId(ACCOUNT_ID)
                             .identifier("t2")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline2")
                             .build();

    NGTriggerEntity t3 = NGTriggerEntity.builder()
                             .name("t3")
                             .enabled(false)
                             .type(NGTriggerType.MANIFEST)
                             .accountId(ACCOUNT_ID)
                             .identifier("t3")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline3")
                             .build();

    NGTriggerEntity t4 = NGTriggerEntity.builder()
                             .name("t4")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t4")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline4")
                             .build();

    NGTriggerEntity t5 = NGTriggerEntity.builder()
                             .name("t5")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t5")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier("pipeline5")
                             .build();

    List<NGTriggerEntity> ngTriggerEntityList = Arrays.asList(t1, t2, t3, t4, t5);

    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().successCount(5l).failureCount(0l).build();

    Stream<NGTriggerEntity> stream = createCloseableIterator(ngTriggerEntityList.iterator()).stream();

    // Mock the behavior of ngTriggerElementMapper.updateEntityYmlWithEnabledValue
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(any(NGTriggerEntity.class));

    // Mock the behavior of ngTriggerRepository.updateTriggerEnabled
    when(ngTriggerRepository.toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean())).thenReturn(triggerUpdateCount);

    // Mock the behavior of ngTriggerRepository.findAll
    when(ngTriggerRepository.findAll(any(Criteria.class))).thenReturn(stream);

    BulkTriggersResponseDTO result = ngTriggerServiceImpl.toggleTriggers(
        enable, ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, scopeInfoFor(ACCOUNT_ID, ORG_ID, PROJECT_ID, PROJECT_ID));

    verify(ngTriggerElementMapper, times(ngTriggerEntityList.size())).updateEntityYmlWithEnabledValue(any());
    verify(ngTriggerRepository).toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean());

    assertEquals(ngTriggerEntityList.size(), result.getBulkTriggerDetailDTOList().size());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testToggleTriggersForPipelineScope() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    boolean enable = true;

    NGTriggerEntity t1 = NGTriggerEntity.builder()
                             .name("t1")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t1")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t2 = NGTriggerEntity.builder()
                             .name("t2")
                             .enabled(false)
                             .type(NGTriggerType.WEBHOOK)
                             .accountId(ACCOUNT_ID)
                             .identifier("t2")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t3 = NGTriggerEntity.builder()
                             .name("t3")
                             .enabled(false)
                             .type(NGTriggerType.MANIFEST)
                             .accountId(ACCOUNT_ID)
                             .identifier("t3")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t4 = NGTriggerEntity.builder()
                             .name("t4")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t4")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t5 = NGTriggerEntity.builder()
                             .name("t5")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t5")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    List<NGTriggerEntity> ngTriggerEntityList = Arrays.asList(t1, t2, t3, t4, t5);

    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().successCount(5l).failureCount(0l).build();

    Stream<NGTriggerEntity> stream = createCloseableIterator(ngTriggerEntityList.iterator()).stream();

    // Mock the behavior of ngTriggerElementMapper.updateEntityYmlWithEnabledValue
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(any(NGTriggerEntity.class));

    // Mock the behavior of ngTriggerRepository.updateTriggerEnabled
    when(ngTriggerRepository.toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean())).thenReturn(triggerUpdateCount);

    // Mock the behavior of ngTriggerRepository.findAll
    when(ngTriggerRepository.findAll(any(Criteria.class))).thenReturn(stream);

    BulkTriggersResponseDTO result = ngTriggerServiceImpl.toggleTriggers(enable, ACCOUNT_ID, ORG_ID, PROJECT_ID,
        PIPELINE_ID, null, scopeInfoFor(ACCOUNT_ID, ORG_ID, PROJECT_ID, PROJECT_ID));

    verify(ngTriggerElementMapper, times(ngTriggerEntityList.size())).updateEntityYmlWithEnabledValue(any());
    verify(ngTriggerRepository).toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean());

    assertEquals(ngTriggerEntityList.size(), result.getBulkTriggerDetailDTOList().size());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testToggleTriggersForSpecificType() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    boolean enable = true;

    NGTriggerEntity t1 = NGTriggerEntity.builder()
                             .name("t1")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t1")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t2 = NGTriggerEntity.builder()
                             .name("t2")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t2")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t3 = NGTriggerEntity.builder()
                             .name("t3")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t3")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t4 = NGTriggerEntity.builder()
                             .name("t4")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t4")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    NGTriggerEntity t5 = NGTriggerEntity.builder()
                             .name("t5")
                             .enabled(false)
                             .type(NGTriggerType.ARTIFACT)
                             .accountId(ACCOUNT_ID)
                             .identifier("t5")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .targetIdentifier(PIPELINE_ID)
                             .build();

    List<NGTriggerEntity> ngTriggerEntityList = Arrays.asList(t1, t2, t3, t4, t5);

    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().successCount(5l).failureCount(0l).build();

    Stream<NGTriggerEntity> stream = createCloseableIterator(ngTriggerEntityList.iterator()).stream();

    // Mock the behavior of ngTriggerElementMapper.updateEntityYmlWithEnabledValue
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(any(NGTriggerEntity.class));

    // Mock the behavior of ngTriggerRepository.updateTriggerEnabled
    when(ngTriggerRepository.toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean())).thenReturn(triggerUpdateCount);

    // Mock the behavior of ngTriggerRepository.findAll
    when(ngTriggerRepository.findAll(any(Criteria.class))).thenReturn(stream);

    BulkTriggersResponseDTO result = ngTriggerServiceImpl.toggleTriggers(enable, ACCOUNT_ID, ORG_ID, PROJECT_ID,
        PIPELINE_ID, null, scopeInfoFor(ACCOUNT_ID, ORG_ID, PROJECT_ID, PROJECT_ID));

    verify(ngTriggerElementMapper, times(ngTriggerEntityList.size())).updateEntityYmlWithEnabledValue(any());
    verify(ngTriggerRepository).toggleTriggerInBulk(anyList(), anyBoolean(), anyBoolean());

    assertEquals(ngTriggerEntityList.size(), result.getBulkTriggerDetailDTOList().size());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecutePollingSubscriptionChangesForMultiRegionArtifactTriggerDisabled() throws Exception {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    PollingItem pollingItem = PollingItem.newBuilder().setPollingDocId("id1").build();
    when(pollingSubscriptionHelper.generatePollingItems(any(), anyBoolean(), eq(null), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    when(pollingSubscriptionHelper.generateMultiArtifactPollingItemsToUnsubscribe(any(), any(), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    byte[] bytes = {70};
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    PollingDocument pollingDocument = PollingDocument.newBuilder().setPollingDocId("id1").build();
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(pollingDocument);

    ResponseDTO<PollingResponseDTO> pollingResponse =
        ResponseDTO.newResponse(PollingResponseDTO.builder().pollingResponse(bytes).build());
    Call<ResponseDTO<PollingResponseDTO>> subscribeCall = mock(Call.class);
    when(subscribeCall.execute()).thenReturn(Response.success(pollingResponse));
    when(pollingResourceClient.subscribe(any(), anyBoolean())).thenReturn(subscribeCall);

    Call<Boolean> unsubscribeCall = mock(Call.class);
    when(unsubscribeCall.execute()).thenReturn(Response.success(true));
    when(pollingResourceClient.unsubscribe(any())).thenReturn(unsubscribeCall);

    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    ngTriggerEntity.setEnabled(false);
    when(ngTriggerRepository.updateValidationStatusAndMetadata(any(), any())).thenReturn(ngTriggerEntity);

    ngTriggerServiceImpl.executePollingSubscriptionChanges(ngTriggerEntity, true, null, false);
    verify(pollingResourceClient, times(0)).subscribe(any(), anyBoolean());
    verify(pollingResourceClient, times(2)).unsubscribe(any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetPollingItemsToUnsubscribeForMultiRegionArtifactTrigger() {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    PollingItem pollingItem = PollingItem.newBuilder().setPollingDocId("id1").build();
    when(pollingSubscriptionHelper.generateMultiArtifactPollingItemsToUnsubscribe(any(), any(), anyBoolean()))
        .thenReturn(List.of(pollingItem, pollingItem));
    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    List<PollingItem> pollingItemsToUnsubscribe = ngTriggerServiceImpl.getPollingItemsToUnsubscribe(
        ngTriggerEntity, List.of(pollingItem, pollingItem), null, false);
    verify(pollingSubscriptionHelper, times(1))
        .generateMultiArtifactPollingItemsToUnsubscribe(any(), any(), anyBoolean());
    assertThat(pollingItemsToUnsubscribe.get(0).getPollingDocId()).isEqualTo("id1");
    assertThat(pollingItemsToUnsubscribe.get(1).getPollingDocId()).isEqualTo("id1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckIfShouldSubscribePollingForMultiRegionArtifactTriggerEnabled() {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    boolean shouldSubscribe = ngTriggerServiceImpl.checkIfShouldSubscribePolling(ngTriggerEntity, null, false);
    assertThat(shouldSubscribe).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckIfShouldSubscribePollingForMultiRegionArtifactTriggerDisabled() {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    ngTriggerEntity.setEnabled(false);
    boolean shouldSubscribe = ngTriggerServiceImpl.checkIfShouldSubscribePolling(ngTriggerEntity, null, false);
    assertThat(shouldSubscribe).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckIfShouldUnsubscribePollingForMultiRegionArtifactTriggerIsUpdate() {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    boolean shouldUnsubscribe =
        ngTriggerServiceImpl.checkIfShouldUnsubscribePolling(ngTriggerEntity, true, null, false);
    assertThat(shouldUnsubscribe).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testStampPollingInfoForMultiArtifactTrigger() {
    NGTriggerElementMapper actualNgTriggerElementMapper =
        new NGTriggerElementMapper(null, null, null, pmsFeatureFlagService, null, null, settingsClient, null);
    NGTriggerEntity ngTriggerEntity = actualNgTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", multiRegionArtifactTriggerYaml, true);
    ngTriggerEntity.getMetadata().setSignatures(List.of("oldSig1", "oldSig2"));
    PollingDocument pollingDocument = PollingDocument.newBuilder().setPollingDocId("id1").build();
    ngTriggerServiceImpl.stampPollingInfoForMultiArtifactTrigger(
        ngTriggerEntity, List.of(pollingDocument, pollingDocument));
    assertThat(ngTriggerEntity.getMetadata().getSignatures().get(0))
        .isEqualTo(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(0).getPollingConfig().getSignature());
    assertThat(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(0).getPollingConfig().getPollingDocId())
        .isEqualTo("id1");
    assertThat(ngTriggerEntity.getMetadata().getSignatures().get(1))
        .isEqualTo(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(1).getPollingConfig().getSignature());
    assertThat(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(1).getPollingConfig().getPollingDocId())
        .isEqualTo("id1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testUpdateTriggerPollingStatusSuccess() {
    PollingTriggerStatusUpdateDTO statusUpdate = PollingTriggerStatusUpdateDTO.builder()
                                                     .signatures(Collections.singletonList("sig"))
                                                     .success(true)
                                                     .errorMessage("")
                                                     .lastCollectedVersions(Collections.singletonList("1.0"))
                                                     .lastCollectedTime(123L)
                                                     .errorStatusValidUntil(null)
                                                     .build();
    when(ngTriggerRepository.updateManyTriggerPollingSubscriptionStatusBySignatures("account",
             statusUpdate.getSignatures(), statusUpdate.isSuccess(), statusUpdate.getErrorMessage(),
             statusUpdate.getLastCollectedVersions(), statusUpdate.getLastCollectedTime(), null))
        .thenReturn(true);
    boolean result = ngTriggerServiceImpl.updateTriggerPollingStatus("account", statusUpdate);
    assertThat(result).isTrue();
    verify(ngTriggerRepository, times(1))
        .updateManyTriggerPollingSubscriptionStatusBySignatures("account", statusUpdate.getSignatures(),
            statusUpdate.isSuccess(), statusUpdate.getErrorMessage(), statusUpdate.getLastCollectedVersions(),
            statusUpdate.getLastCollectedTime(), null);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testUpdateTriggerPollingStatusFailureEmptySignatures() {
    PollingTriggerStatusUpdateDTO statusUpdate = PollingTriggerStatusUpdateDTO.builder()
                                                     .signatures(Collections.emptyList())
                                                     .success(true)
                                                     .errorMessage("")
                                                     .lastCollectedVersions(Collections.singletonList("1.0"))
                                                     .lastCollectedTime(123L)
                                                     .errorStatusValidUntil(100L)
                                                     .build();
    when(ngTriggerRepository.updateManyTriggerPollingSubscriptionStatusBySignatures("account",
             statusUpdate.getSignatures(), statusUpdate.isSuccess(), statusUpdate.getErrorMessage(),
             statusUpdate.getLastCollectedVersions(), statusUpdate.getLastCollectedTime(),
             statusUpdate.getErrorStatusValidUntil()))
        .thenReturn(true);
    assertThatThrownBy(() -> ngTriggerServiceImpl.updateTriggerPollingStatus("account", statusUpdate))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty signatures list provided for trigger polling status update");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateTriggerWithValidation_LimitExceeded() throws Exception {
    String yaml = ngTriggerYamlWithGitSync;
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_HARD_IMPOSE_EXECUTION_LIMITS)).thenReturn(true);
    when(ngTriggerRepository.count(any())).thenReturn(1000L);
    when(pipelineSettingsService.isTriggerCreationWithinLimit(ACCOUNT_ID, 1000L)).thenReturn(false);

    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.createTriggerWithValidation(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, yaml, null, false, false, null))
        .isInstanceOf(LimitExceededException.class)
        .hasMessageContaining("exceeded the max trigger creation limit");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateTriggerWithValidation_InvalidYaml() throws Exception {
    String yaml = "invalid: yaml: content";
    when(ngTriggerRepository.count(any())).thenReturn(5L);
    when(pipelineSettingsService.isTriggerCreationWithinLimit(any(), anyLong())).thenReturn(true);
    when(ngTriggerElementMapper.toTriggerDetails(any(), any(), any(), any(), eq(yaml), eq(false)))
        .thenThrow(new InvalidTriggerYamlException("Invalid yaml structure", null, null, null));
    when(ngTriggerElementMapper.toErrorDTO(any(InvalidTriggerYamlException.class), any(), anyBoolean()))
        .thenReturn(NGTriggerResponseDTO.builder().errorResponse(true).build());

    ResponseDTO<NGTriggerResponseDTO> response = ngTriggerServiceImpl.createTriggerWithValidation(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, yaml, null, false, false, null);

    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().isErrorResponse()).isTrue();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateTriggerWithValidation_AccessDenied() throws Exception {
    String yaml = ngTriggerYamlWithGitSync;
    when(ngTriggerRepository.count(any())).thenReturn(5L);
    doThrow(new NGAccessDeniedException("Access denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.createTriggerWithValidation(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, yaml, null, false, false, null))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Access denied");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testUpdateTriggerWithValidation_AccessDeniedFromExecutorResolver() throws Exception {
    String yaml = ngTriggerYamlWithGitSync;
    NGTriggerEntity existingEntity = ngTriggerEntityGitSync;
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(NGTriggerEntity.builder().identifier(IDENTIFIER).build()).build();

    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENFORCE_TRIGGER_EXECUTOR_IDENTITY))
        .thenReturn(true);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(existingEntity));

    NGTriggerServiceImpl spyService = spy(ngTriggerServiceImpl);
    doReturn(triggerDetails)
        .when(spyService)
        .fetchTriggerEntity(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
    doNothing().when(spyService).validateTriggerConfig(any(), any(), anyBoolean());
    doThrow(new NGAccessDeniedException("Executor lacks pipeline permissions", null, null))
        .when(triggerExecutorResolver)
        .handleExecutorOnUpdate(any(), any(), any(), any(), any(), any(), anyBoolean());

    assertThatThrownBy(()
                           -> spyService.updateTriggerWithValidation("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, IDENTIFIER, yaml, null, false, null))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Executor lacks pipeline permissions");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testUpdateTriggerWithValidation_TriggerNotFound() throws Exception {
    String yaml = ngTriggerYamlWithGitSync;
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.updateTriggerWithValidation("0", ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, yaml, null, false, null))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("Trigger " + IDENTIFIER + " does not exist");
  }

  private static ScopeInfo scopeInfoFor(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String uniqueId) {
    return ScopeInfo.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .uniqueId(uniqueId)
        .build();
  }

  private static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}
