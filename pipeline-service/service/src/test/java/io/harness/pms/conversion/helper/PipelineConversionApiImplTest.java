/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.helper;

import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_EDIT;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.pms.conversion.service.ConversionJobService;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.ConversionJobRequestBody;
import io.harness.spec.server.pipeline.v1.model.EntityIdentifier;
import io.harness.utils.PmsFeatureFlagService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineConversionApiImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String TEMPLATE_ID = "templateId";
  private static final String JOB_ID = "jobId";
  private static final String PIPELINE_RESOURCE = "PIPELINE";
  private static final String TEMPLATE_RESOURCE = "TEMPLATE";
  private static final String TEMPLATE_EDIT_PERMISSION = "core_template_edit";
  private static final String TEMPLATE_VIEW_PERMISSION = "core_template_view";

  @Mock private ConversionJobService conversionJobService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private AccessControlClient accessControlClient;

  private PipelineConversionApiImpl pipelineConversionApi;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineConversionApi =
        new PipelineConversionApiImpl(conversionJobService, pmsFeatureFlagService, accessControlClient);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_V0_TO_V1_CONVERSION)).thenReturn(true);
  }

  private ConversionJobEntity job(
      ConversionActionType actionType, EntityType entityType, String entityIdentifier, ConversionStatus status) {
    return ConversionJobEntity.builder()
        .uuid(JOB_ID)
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .actionType(actionType)
        .entityType(entityType)
        .entityIdentifier(entityIdentifier)
        .status(status)
        .build();
  }

  private ConversionJobRequestBody requestBody(ConversionJobRequestBody.ActionTypeEnum actionType) {
    ConversionJobRequestBody body = new ConversionJobRequestBody();
    body.setAccountId(ACCOUNT_ID);
    body.setOrgId(ORG_ID);
    body.setProjectId(PROJECT_ID);
    body.setActionType(actionType);
    return body;
  }

  private EntityIdentifier entityIdentifier(String entityId, EntityIdentifier.EntityTypeEnum entityType) {
    EntityIdentifier identifier = new EntityIdentifier();
    identifier.setEntityId(entityId);
    identifier.setEntityType(entityType);
    return identifier;
  }

  private void verifyAccessChecked(String resourceType, String entityId, String permission) {
    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)),
            eq(Resource.of(resourceType, entityId)), eq(permission));
  }

  // ---------- createConversionJob ----------

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobSinglePipelineChecksPipelineEdit() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.SINGLE);
    body.setEntityType(ConversionJobRequestBody.EntityTypeEnum.PIPELINE);
    body.setEntityReference(entityIdentifier(PIPELINE_ID, EntityIdentifier.EntityTypeEnum.PIPELINE));
    when(conversionJobService.createJob(any()))
        .thenReturn(job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.QUEUED));

    Response response = pipelineConversionApi.createConversionJob(body, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_EDIT);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobSingleTemplateChecksTemplateEdit() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.SINGLE);
    body.setEntityType(ConversionJobRequestBody.EntityTypeEnum.TEMPLATE);
    body.setEntityReference(entityIdentifier(TEMPLATE_ID, EntityIdentifier.EntityTypeEnum.TEMPLATE));
    when(conversionJobService.createJob(any()))
        .thenReturn(job(ConversionActionType.SINGLE, EntityType.TEMPLATE, TEMPLATE_ID, ConversionStatus.QUEUED));

    Response response = pipelineConversionApi.createConversionJob(body, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(TEMPLATE_RESOURCE, TEMPLATE_ID, TEMPLATE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobBatchChecksEveryReferencedEntityInOneAclCall() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.BATCH);
    body.setEntityReferences(Arrays.asList(entityIdentifier(PIPELINE_ID, EntityIdentifier.EntityTypeEnum.PIPELINE),
        entityIdentifier(TEMPLATE_ID, EntityIdentifier.EntityTypeEnum.TEMPLATE)));
    when(conversionJobService.createJob(any()))
        .thenReturn(job(ConversionActionType.BATCH, EntityType.PIPELINE, null, ConversionStatus.QUEUED));

    Response response = pipelineConversionApi.createConversionJob(body, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PermissionCheckDTO>> permissionChecksCaptor = ArgumentCaptor.forClass(List.class);
    verify(accessControlClient, times(1)).checkForAccessOrThrow(permissionChecksCaptor.capture(), anyString());
    verify(accessControlClient, never())
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString());

    List<PermissionCheckDTO> permissionChecks = permissionChecksCaptor.getValue();
    assertThat(permissionChecks).hasSize(2);
    assertThat(permissionChecks)
        .extracting(PermissionCheckDTO::getResourceType, PermissionCheckDTO::getResourceIdentifier,
            PermissionCheckDTO::getPermission, PermissionCheckDTO::getResourceScope)
        .containsExactlyInAnyOrder(
            tuple(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_EDIT, ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)),
            tuple(TEMPLATE_RESOURCE, TEMPLATE_ID, TEMPLATE_EDIT_PERMISSION,
                ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobBatchAccessDeniedDoesNotCreateJob() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.BATCH);
    body.setEntityReferences(Arrays.asList(entityIdentifier(PIPELINE_ID, EntityIdentifier.EntityTypeEnum.PIPELINE),
        entityIdentifier(TEMPLATE_ID, EntityIdentifier.EntityTypeEnum.TEMPLATE)));
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(anyList(), anyString());

    assertThatThrownBy(() -> pipelineConversionApi.createConversionJob(body, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verify(conversionJobService, never()).createJob(any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobBatchNullEntityTypeThrowsInvalidRequest() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.BATCH);
    EntityIdentifier referenceWithoutType = new EntityIdentifier();
    referenceWithoutType.setEntityId(PIPELINE_ID);
    body.setEntityReferences(Collections.singletonList(referenceWithoutType));

    assertThatThrownBy(() -> pipelineConversionApi.createConversionJob(body, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("entityType is required");
    verifyNoInteractions(accessControlClient);
    verify(conversionJobService, never()).createJob(any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobSingleNullEntityTypeThrowsInvalidRequest() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.SINGLE);
    EntityIdentifier referenceWithoutType = new EntityIdentifier();
    referenceWithoutType.setEntityId(PIPELINE_ID);
    body.setEntityReference(referenceWithoutType);

    assertThatThrownBy(() -> pipelineConversionApi.createConversionJob(body, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("entityType is required");
    verifyNoInteractions(accessControlClient);
    verify(conversionJobService, never()).createJob(any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobProjectChecksScopeLevelPipelineEdit() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.PROJECT);
    when(conversionJobService.createJob(any()))
        .thenReturn(job(ConversionActionType.PROJECT, EntityType.PIPELINE, null, ConversionStatus.QUEUED));

    Response response = pipelineConversionApi.createConversionJob(body, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, null, PIPELINE_EDIT);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobAccessDeniedDoesNotCreateJob() {
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.SINGLE);
    body.setEntityReference(entityIdentifier(PIPELINE_ID, EntityIdentifier.EntityTypeEnum.PIPELINE));
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(PIPELINE_EDIT));

    assertThatThrownBy(() -> pipelineConversionApi.createConversionJob(body, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verify(conversionJobService, never()).createJob(any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCreateConversionJobFeatureFlagDisabledSkipsAccessCheck() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_V0_TO_V1_CONVERSION)).thenReturn(false);
    ConversionJobRequestBody body = requestBody(ConversionJobRequestBody.ActionTypeEnum.PROJECT);

    assertThatThrownBy(() -> pipelineConversionApi.createConversionJob(body, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(accessControlClient);
    verify(conversionJobService, never()).createJob(any());
  }

  // ---------- getConversionJob ----------

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetConversionJobChecksPipelineView() {
    when(conversionJobService.getJobByUuid(JOB_ID))
        .thenReturn(
            Optional.of(job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.SUCCESS)));
    when(conversionJobService.getChildJobs(JOB_ID)).thenReturn(Collections.emptyList());

    Response response = pipelineConversionApi.getConversionJob(JOB_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_VIEW);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetConversionJobForTemplateChecksTemplateView() {
    when(conversionJobService.getJobByUuid(JOB_ID))
        .thenReturn(
            Optional.of(job(ConversionActionType.SINGLE, EntityType.TEMPLATE, TEMPLATE_ID, ConversionStatus.SUCCESS)));
    when(conversionJobService.getChildJobs(JOB_ID)).thenReturn(Collections.emptyList());

    Response response = pipelineConversionApi.getConversionJob(JOB_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(TEMPLATE_RESOURCE, TEMPLATE_ID, TEMPLATE_VIEW_PERMISSION);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetConversionJobNotFoundSkipsAccessCheck() {
    when(conversionJobService.getJobByUuid(JOB_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> pipelineConversionApi.getConversionJob(JOB_ID, ACCOUNT_ID))
        .isInstanceOf(EntityNotFoundException.class);
    verifyNoInteractions(accessControlClient);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetConversionJobAccessDeniedDoesNotReadChildJobs() {
    when(conversionJobService.getJobByUuid(JOB_ID))
        .thenReturn(
            Optional.of(job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.SUCCESS)));
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(PIPELINE_VIEW));

    assertThatThrownBy(() -> pipelineConversionApi.getConversionJob(JOB_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verify(conversionJobService, never()).getChildJobs(any());
  }

  // ---------- getConversionJobByEntity ----------

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetConversionJobByEntityChecksPipelineView() {
    when(conversionJobService.getJobByEntityScope(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, EntityType.PIPELINE))
        .thenReturn(
            Optional.of(job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.SUCCESS)));
    when(conversionJobService.getChildJobs(JOB_ID)).thenReturn(Collections.emptyList());

    Response response =
        pipelineConversionApi.getConversionJobByEntity(PIPELINE_ID, "PIPELINE", ORG_ID, PROJECT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_VIEW);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetConversionJobByEntityAccessDeniedBeforeLookup() {
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(PIPELINE_VIEW));

    assertThatThrownBy(
        () -> pipelineConversionApi.getConversionJobByEntity(PIPELINE_ID, "PIPELINE", ORG_ID, PROJECT_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verify(conversionJobService, never()).getJobByEntityScope(any(), any(), any(), any(), any());
  }

  // ---------- retryConversionJob ----------

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRetryConversionJobChecksPipelineEdit() {
    ConversionJobEntity failedJob =
        job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.FAILED);
    when(conversionJobService.getJobByUuid(JOB_ID)).thenReturn(Optional.of(failedJob));
    when(conversionJobService.retryJob(JOB_ID)).thenReturn(failedJob);

    Response response = pipelineConversionApi.retryConversionJob(JOB_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_EDIT);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRetryConversionJobAccessDeniedDoesNotRetry() {
    when(conversionJobService.getJobByUuid(JOB_ID))
        .thenReturn(
            Optional.of(job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.FAILED)));
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(PIPELINE_EDIT));

    assertThatThrownBy(() -> pipelineConversionApi.retryConversionJob(JOB_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verify(conversionJobService, never()).retryJob(any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRetryConversionJobInNonRetryableStatusStillAuthorizesFirst() {
    when(conversionJobService.getJobByUuid(JOB_ID))
        .thenReturn(
            Optional.of(job(ConversionActionType.SINGLE, EntityType.PIPELINE, PIPELINE_ID, ConversionStatus.SUCCESS)));

    assertThatThrownBy(() -> pipelineConversionApi.retryConversionJob(JOB_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
    verifyAccessChecked(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_EDIT);
    verify(conversionJobService, never()).retryJob(any());
  }

  // ---------- deleteConversionChecksums ----------

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteConversionChecksumsForPipelineChecksPipelineEdit() {
    when(conversionJobService.deleteChecksums(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, EntityType.PIPELINE, null))
        .thenReturn(3L);

    Response response =
        pipelineConversionApi.deleteConversionChecksums(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "PIPELINE", null);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, PIPELINE_ID, PIPELINE_EDIT);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteConversionChecksumsForTemplateChecksTemplateEdit() {
    when(conversionJobService.deleteChecksums(ACCOUNT_ID, ORG_ID, PROJECT_ID, TEMPLATE_ID, EntityType.TEMPLATE, "v1"))
        .thenReturn(1L);

    Response response =
        pipelineConversionApi.deleteConversionChecksums(ACCOUNT_ID, ORG_ID, PROJECT_ID, TEMPLATE_ID, "TEMPLATE", "v1");

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(TEMPLATE_RESOURCE, TEMPLATE_ID, TEMPLATE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteConversionChecksumsBulkAtProjectScopeChecksScopeLevelPipelineEdit() {
    when(conversionJobService.deleteChecksums(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null)).thenReturn(10L);

    Response response =
        pipelineConversionApi.deleteConversionChecksums(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyAccessChecked(PIPELINE_RESOURCE, null, PIPELINE_EDIT);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteConversionChecksumsAccessDeniedDoesNotDelete() {
    doThrow(new InvalidRequestException("denied"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(PIPELINE_EDIT));

    assertThatThrownBy(()
                           -> pipelineConversionApi.deleteConversionChecksums(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "PIPELINE", null))
        .isInstanceOf(InvalidRequestException.class);
    verify(conversionJobService, never()).deleteChecksums(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteConversionChecksumsInvalidRequestSkipsAccessCheck() {
    assertThatThrownBy(
        () -> pipelineConversionApi.deleteConversionChecksums(ACCOUNT_ID, ORG_ID, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(accessControlClient);
    verify(conversionJobService, never()).deleteChecksums(any(), any(), any(), isNull(), any(), any());
  }
}
