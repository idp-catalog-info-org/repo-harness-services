/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_BLUEPRINT_KIND;
import static io.harness.rule.OwnerRule.ARYA;
import static io.harness.rule.OwnerRule.CHRISTIAN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.GetEntityVersionsDTO;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityVersionRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.spec.server.idp.v1.model.EntityVersionCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;
import io.harness.springdata.TransactionHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogVersionServiceImplTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  public static final String TEST_ORG_IDENTIFIER = "testOrg123";
  public static final String TEST_PROJECT_IDENTIFIER = "testProject123";
  public static final String TEST_IDENTIFIER = "testIdentifier123";
  public static final String TEST_VERSION = "v1";
  public static final String TEST_SCOPE = "account"
      + "." + TEST_ORG_IDENTIFIER + "." + TEST_PROJECT_IDENTIFIER;
  public static final String ENTITY_ID = "entityId";
  AutoCloseable openMocks;

  @InjectMocks CatalogVersionServiceImpl catalogVersionService;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock CatalogEntityVersionRepository catalogEntityVersionRepository;
  @Mock IdpCommonService idpCommonService;
  @Mock OutboxService outboxService;
  @Mock TransactionHelper transactionHelper;
  @Mock ScopeInfoClient scopeInfoClient;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testCreateEntityVersion() throws IOException {
    String entityYaml = "apiVersion: harness.io/v1\n"
        + "kind: environmentblueprint\n"
        + "type: ''\n"
        + "identifier: testIdentifier123\n"
        + "name: testIdentifier123\n"
        + "owner: group:account/_account_all_users\n"
        + "description: 'This is a test environment blueprint.'\n"
        + "spec:\n"
        + "  entities:\n"
        + "  - identifier: git\n"
        + "    backend:\n"
        + "      type: HarnessCD\n"
        + "      steps:\n"
        + "        apply:\n"
        + "          pipeline: gittest\n"
        + "          branch: main\n"
        + "        destroy:\n"
        + "          pipeline: gittest\n"
        + "          branch: not-main\n"
        + "  ownedBy:\n"
        + "  - group:account/_account_all_users\n";
    EntityVersionCreateRequest createRequest = new EntityVersionCreateRequest();
    createRequest.setYaml(entityYaml);
    createRequest.setVersion(TEST_VERSION);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogServiceHelper.validateAndSanitizeKind("environmentblueprint")).thenReturn("environmentblueprint");

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    catalog.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    catalog.setIdentifier(TEST_IDENTIFIER);
    catalog.setKind(ENVIRONMENT_BLUEPRINT_KIND);
    catalog.setParentUniqueId(TEST_ACCOUNT_IDENTIFIER);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    CatalogEntityVersion catalogEntityVersion = CatalogEntityVersion.builder()
                                                    .entityId(ENTITY_ID)
                                                    .version(TEST_VERSION)
                                                    .deprecated(false)
                                                    .yaml(entityYaml)
                                                    .build();

    when(catalogEntityVersionRepository.createCatalogEntityVersionAndSyncStable(any()))
        .thenReturn(catalogEntityVersion);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    EntityVersionResponse entityVersionResponse =
        catalogVersionService.createEntityVersion(catalog, entityYaml, TEST_VERSION, null, false, false, null, null);

    assert entityVersionResponse != null;
    assert entityVersionResponse.getIdentifier().equals(TEST_IDENTIFIER);
    assert entityVersionResponse.getVersion().equals(TEST_VERSION);
    assert entityVersionResponse.isDeprecated().equals(false);
    assert entityVersionResponse.getDescription() == null;
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testUpdateEntityVersionUpdate() throws IOException {
    when(catalogServiceHelper.validateAndSanitizeKind("EnvironmentBlueprint")).thenReturn("environmentblueprint");
    when(catalogServiceHelper.validateAndSanitizeKind("environmentblueprint")).thenReturn("environmentblueprint");

    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setVersion(TEST_VERSION);
    catalogEntityVersion.setDescription("Old description");
    catalogEntityVersion.setDeprecated(false);
    catalogEntityVersion.setStable(false);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER))
        .thenReturn(scopeInfoCall);

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    catalog.setIdentifier(TEST_IDENTIFIER);
    catalog.setParentUniqueId(TEST_ACCOUNT_IDENTIFIER);
    catalog.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    catalog.setKind(ENVIRONMENT_BLUEPRINT_KIND);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    when(catalogEntityVersionRepository.findByEntityIdAndVersion(ENTITY_ID, TEST_VERSION))
        .thenReturn(catalogEntityVersion);

    EntityVersionUpdateRequest updateRequest = new EntityVersionUpdateRequest();
    updateRequest.setDescription("Updated description");
    updateRequest.setDeprecated(true);
    updateRequest.setStable(null);
    String yaml = "apiVersion: harness.io/v1\n"
        + "kind: environmentblueprint\n"
        + "type: ''\n"
        + "identifier: testIdentifier123\n"
        + "name: testIdentifier123\n"
        + "orgIdentifier: testOrg123\n"
        + "projectIdentifier: testProject123\n"
        + "owner: group:account/_account_all_users\n"
        + "description: 'This is a test environment blueprint.'\n"
        + "spec:\n"
        + "  entities:\n"
        + "  - identifier: git\n"
        + "    backend:\n"
        + "      type: HarnessCD\n"
        + "      steps:\n"
        + "        apply:\n"
        + "          pipeline: gittest\n"
        + "          branch: main\n"
        + "        destroy:\n"
        + "          pipeline: gittest\n"
        + "          branch: not-main\n"
        + "  ownedBy:\n"
        + "  - group:account/_account_all_users\n";
    updateRequest.setYaml(yaml);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    EntityVersionResponse entityVersionResponse = catalogVersionService.updateEntityVersion(
        TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_VERSION, updateRequest, catalog);

    assert entityVersionResponse != null;
    assert entityVersionResponse.getDescription().equals("Updated description");
    assert entityVersionResponse.isDeprecated().equals(true);
    assert entityVersionResponse.isStable().equals(false);
    assert entityVersionResponse.getYaml().equals(yaml);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testUpdateEntityVersionDeprecatedAndStable() throws IOException {
    when(catalogServiceHelper.validateAndSanitizeKind("EnvironmentBlueprint")).thenReturn("environmentblueprint");
    when(catalogServiceHelper.validateAndSanitizeKind("environmentblueprint")).thenReturn("environmentblueprint");

    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setVersion(TEST_VERSION);
    catalogEntityVersion.setDescription("Old description");
    catalogEntityVersion.setDeprecated(false);
    catalogEntityVersion.setStable(true);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER))
        .thenReturn(scopeInfoCall);

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    catalog.setIdentifier(TEST_IDENTIFIER);
    catalog.setParentUniqueId(TEST_ACCOUNT_IDENTIFIER);
    catalog.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    catalog.setKind(ENVIRONMENT_BLUEPRINT_KIND);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    when(catalogEntityVersionRepository.findByEntityIdAndVersion(ENTITY_ID, TEST_VERSION))
        .thenReturn(catalogEntityVersion);

    EntityVersionUpdateRequest updateRequest = new EntityVersionUpdateRequest();
    updateRequest.setDeprecated(true);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    catalogVersionService.updateEntityVersion(
        TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_VERSION, updateRequest, catalog);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testUpdateEntityVersionDeprecatedCannotBeMarkedStable() throws IOException {
    when(catalogServiceHelper.validateAndSanitizeKind("environmentblueprint")).thenReturn("environmentblueprint");

    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setVersion(TEST_VERSION);
    catalogEntityVersion.setDescription("Old description");
    catalogEntityVersion.setDeprecated(true);
    catalogEntityVersion.setStable(false);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER))
        .thenReturn(scopeInfoCall);

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    catalog.setIdentifier(TEST_IDENTIFIER);
    catalog.setParentUniqueId(TEST_ACCOUNT_IDENTIFIER);
    catalog.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    catalog.setKind(ENVIRONMENT_BLUEPRINT_KIND);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    when(catalogEntityVersionRepository.findByEntityIdAndVersion(ENTITY_ID, TEST_VERSION))
        .thenReturn(catalogEntityVersion);

    EntityVersionUpdateRequest updateRequest = new EntityVersionUpdateRequest();
    updateRequest.setStable(true);

    when(transactionHelper.performTransaction(any(TransactionHelper.TransactionFunction.class)))
        .thenAnswer(invocation -> {
          TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
          return function.execute();
        });

    catalogVersionService.updateEntityVersion(
        TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_VERSION, updateRequest, catalog);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testUpdateEntityVersionDeprecatedCannotBeEdited() throws IOException {
    when(catalogServiceHelper.validateAndSanitizeKind("environmentblueprint")).thenReturn("environmentblueprint");

    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setVersion(TEST_VERSION);
    catalogEntityVersion.setDescription("Old description");
    catalogEntityVersion.setDeprecated(true);
    catalogEntityVersion.setStable(false);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER))
        .thenReturn(scopeInfoCall);

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    catalog.setIdentifier(TEST_IDENTIFIER);
    catalog.setParentUniqueId(TEST_ACCOUNT_IDENTIFIER);
    catalog.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    catalog.setKind(ENVIRONMENT_BLUEPRINT_KIND);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    when(catalogEntityVersionRepository.findByEntityIdAndVersion(ENTITY_ID, TEST_VERSION))
        .thenReturn(catalogEntityVersion);

    EntityVersionUpdateRequest updateRequest = new EntityVersionUpdateRequest();
    updateRequest.setYaml("apiVersion: harness.io/v1\nkind: environmentblueprint\nidentifier: testIdentifier123\n");

    when(transactionHelper.performTransaction(any(TransactionHelper.TransactionFunction.class)))
        .thenAnswer(invocation -> {
          TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
          return function.execute();
        });

    catalogVersionService.updateEntityVersion(
        TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER, TEST_VERSION, updateRequest, catalog);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testGetEntityVersion() throws IOException {
    when(catalogServiceHelper.validateAndSanitizeKind("EnvironmentBlueprint")).thenReturn("environmentblueprint");

    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setVersion(TEST_VERSION);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER))
        .thenReturn(scopeInfoCall);

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    when(catalogEntityVersionRepository.findByEntityIdAndVersion(ENTITY_ID, TEST_VERSION))
        .thenReturn(catalogEntityVersion);

    EntityVersionResponse entityVersionResponse =
        catalogVersionService.getEntityVersion(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER,
            TEST_SCOPE, "EnvironmentBlueprint", TEST_IDENTIFIER, TEST_VERSION);

    assert entityVersionResponse != null;
    assert entityVersionResponse.getVersion().equals(TEST_VERSION);
  }

  @Test
  @Owner(developers = CHRISTIAN)
  @Category(UnitTests.class)
  public void testGetEntityVersions() throws IOException {
    when(catalogServiceHelper.validateAndSanitizeKind("EnvironmentBlueprint")).thenReturn("environmentblueprint");

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER))
        .thenReturn(scopeInfoCall);

    CatalogEntity catalog = new InlineCatalogEntity();
    catalog.setId(ENTITY_ID);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "environmentblueprint", TEST_IDENTIFIER))
        .thenReturn(catalog);

    List<CatalogEntityVersion> catalogEntityVersionList = new ArrayList<>();
    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setVersion(TEST_VERSION);

    catalogEntityVersionList.add(catalogEntityVersion);

    Page<CatalogEntityVersion> catalogEntityVersionPage =
        new PageImpl<>(catalogEntityVersionList, PageRequest.of(0, 10), 1);

    when(catalogEntityVersionRepository.findByEntityId(ENTITY_ID, null, null, null, null))
        .thenReturn(catalogEntityVersionPage);

    GetEntityVersionsDTO getEntityVersionsDTO =
        catalogVersionService.getEntityVersions(TEST_ACCOUNT_IDENTIFIER, TEST_ORG_IDENTIFIER, TEST_PROJECT_IDENTIFIER,
            TEST_SCOPE, "EnvironmentBlueprint", TEST_IDENTIFIER, null, null, null, null);

    assert getEntityVersionsDTO != null;
    assert getEntityVersionsDTO.getEntityVersionResponses() != null;
    assert getEntityVersionsDTO.getEntityVersionResponses().size() == 1;
    assert getEntityVersionsDTO.getEntityVersionResponses().get(0).getVersion().equals(TEST_VERSION);

    assert getEntityVersionsDTO.getPageNumber() == 0;
    assert getEntityVersionsDTO.getTotalElements() == 1;
  }
}
