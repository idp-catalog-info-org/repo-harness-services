/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.processor.ApiSpecGitRefresher;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncFailedException;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncInProgressException;
import io.harness.idp.common.IdpCommonService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ApiEndpointSyncResponse;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointSyncServiceImplTest extends CategoryTest {
  private static final String ACCOUNT = "account-A";
  private static final String ORG = "org-A";
  private static final String PROJECT = "project-A";
  private static final String KIND = "api";
  private static final String IDENTIFIER = "my-api";

  private CatalogServiceHelper catalogServiceHelper;
  private IdpCommonService idpCommonService;
  private ApiSpecGitRefresher apiSpecGitRefresher;
  private ApiEndpointProcessor apiEndpointProcessor;
  private ApiEndpointSyncServiceImpl service;

  @Before
  public void setUp() {
    catalogServiceHelper = mock(CatalogServiceHelper.class);
    idpCommonService = mock(IdpCommonService.class);
    apiSpecGitRefresher = mock(ApiSpecGitRefresher.class);
    apiEndpointProcessor = mock(ApiEndpointProcessor.class);
    service = new ApiEndpointSyncServiceImpl(
        catalogServiceHelper, idpCommonService, apiSpecGitRefresher, apiEndpointProcessor);

    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT)).thenReturn(true);
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(apiEntity());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_ffOff_throwsInvalidRequestException_beforeRbacCheck() {
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT)).thenReturn(false);

    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);

    // FF check must short-circuit before the RBAC round-trip.
    verifyNoInteractions(apiSpecGitRefresher, apiEndpointProcessor);
    verify(catalogServiceHelper, never()).checkCrudRbac(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_nonApiKind_throwsInvalidRequestException_beforeRbacCheck() {
    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, "component", IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);

    verifyNoInteractions(apiSpecGitRefresher, apiEndpointProcessor);
    verify(catalogServiceHelper, never()).checkCrudRbac(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_rbacDenied_propagatesUncaught() {
    doThrow(new NGAccessDeniedException("denied", null, Collections.emptyList()))
        .when(catalogServiceHelper)
        .checkCrudRbac(eq(ACCOUNT), eq(ORG), eq(PROJECT), eq(KIND), anyString(), eq("edit"));

    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER))
        .isInstanceOf(NGAccessDeniedException.class);

    verifyNoInteractions(apiSpecGitRefresher, apiEndpointProcessor);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_entityNotFound_throwsEntityNotFoundException() {
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(null);

    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER))
        .isInstanceOf(EntityNotFoundException.class);

    verifyNoInteractions(apiSpecGitRefresher, apiEndpointProcessor);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_gitRefreshThrows_wrapsAsApiEndpointSyncFailedException() {
    doThrow(new RuntimeException("git fetch failed")).when(apiSpecGitRefresher).refresh(any(), eq(true));

    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER))
        .isInstanceOf(ApiEndpointSyncFailedException.class)
        .hasMessageContaining("git fetch failed");

    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_success_changedTrueAndCountFromNewKeys() {
    CatalogEntity entity = apiEntity();
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(entity);
    when(apiEndpointProcessor.processEntity(entity))
        .thenReturn(ProcessingOutcome.success(
            false, Collections.emptyList(), List.of("GET /a", "POST /b"), Collections.emptyList()));

    ApiEndpointSyncResponse response = service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER);

    assertThat(response.isChanged()).isTrue();
    assertThat(response.getEndpointsExtracted()).isEqualTo(2);
    verify(apiSpecGitRefresher).refresh(entity, true);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_degraded_reportedAsSuccessWithWarnings() {
    CatalogEntity entity = apiEntity();
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(entity);
    when(apiEndpointProcessor.processEntity(entity))
        .thenReturn(ProcessingOutcome.success(
            true, Collections.emptyList(), List.of("GET /a"), List.of("server URL template variable had no default")));

    ApiEndpointSyncResponse response = service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER);

    assertThat(response.isChanged()).isTrue();
    assertThat(response.getEndpointsExtracted()).isEqualTo(1);
    assertThat(response.getWarnings()).containsExactly("server URL template variable had no default");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_hashSkipped_changedFalseAndCountFromOldKeys() {
    CatalogEntity entity = apiEntity();
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(entity);
    when(apiEndpointProcessor.processEntity(entity))
        .thenReturn(ProcessingOutcome.hashSkipped(List.of("GET /a", "GET /b", "POST /c"), Collections.emptyList()));

    ApiEndpointSyncResponse response = service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER);

    assertThat(response.isChanged()).isFalse();
    assertThat(response.getEndpointsExtracted()).isEqualTo(3);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_hashSkippedOnPreviouslyPartialEntity_stillReportsWarnings() {
    CatalogEntity entity = apiEntity();
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(entity);
    when(apiEndpointProcessor.processEntity(entity))
        .thenReturn(ProcessingOutcome.hashSkipped(
            List.of("GET /a", "GET /b"), List.of("server URL template variable had no default")));

    ApiEndpointSyncResponse response = service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER);

    assertThat(response.isChanged()).isFalse();
    assertThat(response.getEndpointsExtracted()).isEqualTo(2);
    assertThat(response.getWarnings()).containsExactly("server URL template variable had no default");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_failed_throwsApiEndpointSyncFailedException() {
    CatalogEntity entity = apiEntity();
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(entity);
    when(apiEndpointProcessor.processEntity(entity)).thenReturn(ProcessingOutcome.failure("could not parse spec"));

    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER))
        .isInstanceOf(ApiEndpointSyncFailedException.class)
        .hasMessageContaining("could not parse spec");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSync_lockSkipped_throwsApiEndpointSyncInProgressException() {
    CatalogEntity entity = apiEntity();
    when(catalogServiceHelper.catalogEntity(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER)).thenReturn(entity);
    when(apiEndpointProcessor.processEntity(entity)).thenReturn(ProcessingOutcome.lockSkipped());

    assertThatThrownBy(() -> service.sync(ACCOUNT, ORG, PROJECT, KIND, IDENTIFIER))
        .isInstanceOf(ApiEndpointSyncInProgressException.class);
  }

  private static CatalogEntity apiEntity() {
    return InlineCatalogEntity.builder()
        .accountIdentifier(ACCOUNT)
        .orgIdentifier(ORG)
        .projectIdentifier(PROJECT)
        .kind(KIND)
        .identifier(IDENTIFIER)
        .build();
  }
}
