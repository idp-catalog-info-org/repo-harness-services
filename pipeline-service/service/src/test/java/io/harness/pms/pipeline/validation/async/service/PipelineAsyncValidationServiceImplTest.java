/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.service;

import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PipelineServiceTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.governance.GovernanceMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.impl.PipelineAsyncValidationServiceImpl;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.repositories.pipeline.validation.async.PipelineValidationEventRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class PipelineAsyncValidationServiceImplTest extends PipelineServiceTestBase {
  @Inject PipelineValidationEventRepository pipelineValidationEventRepository;
  @Inject PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject PipelineGovernanceService pipelineGovernanceService;
  PipelineValidationService pipelineValidationService;
  PipelineAsyncValidationServiceImpl asyncValidationService;
  PipelineRefreshService pipelineRefreshService;
  PmsFeatureFlagService pmsFeatureFlagService;

  PipelineEntity pipeline;
  ScopeInfo scopeInfo;
  String fqn;

  @Before
  public void setUp() {
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .uniqueId("org/proj")
                    .build();
    pipeline = PipelineEntity.builder()
                   .accountId("acc")
                   .orgIdentifier("org")
                   .projectIdentifier("proj")
                   .identifier("pipeline")
                   .yaml("yaml")
                   .parentUniqueId("org/proj")
                   .build();
    fqn = "acc/org/proj/pipeline";
    Executor executor = Mockito.mock(Executor.class);
    io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler pipelineOpaStatusHandler =
        Mockito.mock(io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler.class);
    asyncValidationService = new PipelineAsyncValidationServiceImpl(pipelineValidationEventRepository, executor,
        pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService, pipelineValidationService,
        pmsFeatureFlagService, pipelineOpaStatusHandler);
    Reflect.on(asyncValidationService).set("executor", executor);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    PipelineValidationEvent validationEvent =
        asyncValidationService.startEvent(pipeline, null, Action.CRUD, false, scopeInfo, true);
    assertThat(validationEvent).isNotNull();
    assertThat(validationEvent.getUuid()).isNotEmpty();
    assertThat(validationEvent.getFqn()).isEqualTo(fqn);
    assertThat(validationEvent.getStartTs()).isNotNull();
    assertThat(validationEvent.getParams()).isEqualTo(ValidationParams.builder().pipelineEntity(pipeline).build());
    assertThat(validationEvent.getResult()).isEqualTo(ValidationResult.builder().build());

    String validationEventUuid = validationEvent.getUuid();
    Optional<PipelineValidationEvent> optionalEventByUuid = asyncValidationService.getEventByUuid(validationEventUuid);
    assertThat(optionalEventByUuid.isPresent()).isTrue();
    PipelineValidationEvent eventByUuid = optionalEventByUuid.get();
    assertThat(eventByUuid.getUuid()).isEqualTo(validationEventUuid);
    assertThat(eventByUuid.getFqn()).isEqualTo(fqn);
    assertThat(eventByUuid.getStartTs()).isNotNull();
    assertThat(eventByUuid.getParams()).isEqualTo(ValidationParams.builder().pipelineEntity(pipeline).build());
    assertThat(eventByUuid.getResult()).isEqualTo(ValidationResult.builder().build());

    PipelineValidationEvent updatedEvent = asyncValidationService.updateEvent(
        validationEventUuid, ValidationStatus.SUCCESS, ValidationResult.builder().build());
    assertThat(updatedEvent).isNotNull();
    assertThat(updatedEvent.getUuid()).isEqualTo(validationEventUuid);
    assertThat(updatedEvent.getStatus()).isEqualTo(ValidationStatus.SUCCESS);
    assertThat(updatedEvent.getFqn()).isEqualTo(fqn);
    assertThat(updatedEvent.getStartTs()).isNotNull();
    assertThat(updatedEvent.getParams()).isEqualTo(ValidationParams.builder().pipelineEntity(pipeline).build());
    assertThat(updatedEvent.getResult()).isEqualTo(ValidationResult.builder().build());

    PipelineValidationEvent newEvent =
        asyncValidationService.startEvent(pipeline, null, Action.CRUD, false, scopeInfo, true);
    assertThat(newEvent).isNotNull();
    String newEventUuid = newEvent.getUuid();
    Optional<PipelineValidationEvent> optionalLatest =
        asyncValidationService.getLatestEventByFQNAndAction(fqn, Action.CRUD);
    assertThat(optionalLatest.isPresent()).isTrue();
    PipelineValidationEvent latestEvent = optionalLatest.get();
    assertThat(latestEvent.getUuid()).isEqualTo(newEventUuid);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateRecordForSuccessfulSyncValidation() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setStatus("PASS").build();
    PipelineValidationEvent validationEvent = asyncValidationService.createRecordForSuccessfulSyncValidation(
        pipeline, null, governanceMetadata, Action.CRUD, false);
    assertThat(validationEvent.getStatus()).isEqualTo(ValidationStatus.SUCCESS);
    assertThat(validationEvent.getFqn()).isEqualTo(fqn);
    assertThat(validationEvent.getParams().getPipelineEntity()).isEqualTo(pipeline);
    assertThat(validationEvent.getResult().getTemplateValidationResponse().isValidYaml()).isTrue();
    assertThat(validationEvent.getResult().getGovernanceMetadata()).isEqualTo(governanceMetadata);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCreateRecordForSuccessfulSyncValidation_remotePipeline_populatesOpaEvalFields() {
    PipelineEntity remotePipeline = PipelineEntity.builder()
                                        .accountId("acc")
                                        .orgIdentifier("org")
                                        .projectIdentifier("proj")
                                        .identifier("pipeline")
                                        .yaml("yaml")
                                        .storeType(io.harness.gitsync.beans.StoreType.REMOTE)
                                        .build();

    io.harness.gitaware.helper.GitAwareContextHelper.updateScmGitMetaData(
        io.harness.gitsync.scm.beans.ScmGitMetaData.builder().commitId("sync_commit_sha").build());

    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).setStatus("PASS").build();

    long before = System.currentTimeMillis();
    PipelineValidationEvent validationEvent = asyncValidationService.createRecordForSuccessfulSyncValidation(
        remotePipeline, null, governanceMetadata, Action.CRUD, false);
    long after = System.currentTimeMillis();

    assertThat(validationEvent.getStatus()).isEqualTo(ValidationStatus.SUCCESS);
    assertThat(validationEvent.getResult().getOpaEvaluatedAt()).isNotNull();
    assertThat(validationEvent.getResult().getOpaEvaluatedAt()).isBetween(before, after);
    assertThat(validationEvent.getResult().getOpaLastValidCommitId()).isEqualTo("sync_commit_sha");
  }
}