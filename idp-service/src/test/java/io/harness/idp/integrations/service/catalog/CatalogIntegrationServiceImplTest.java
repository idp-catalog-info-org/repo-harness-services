/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.catalog;

import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.HARJAS;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.clients.integrationmanager.EntityMappedEntityResponse;
import io.harness.clients.integrationmanager.EntityMappedEntityResponseObject;
import io.harness.clients.integrationmanager.EntitySubscribeEntitiesResponse;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.OpenapiGetMappedEntitiesRequest;
import io.harness.clients.integrationmanager.TypesIntegrationConfig;
import io.harness.eventsframework.schemas.idp.UserPrincipal;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.catalog.HarnessCDIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationRequest;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationResponse;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponse;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponseActionDestinationMerge;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityMoveRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.HarnessCDIntegrationRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequestIntegrationEntities;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;
import io.harness.springdata.TransactionHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class CatalogIntegrationServiceImplTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String TEST_IDENTIFIER = "_harness_cd";

  @Mock private IntegrationEntityRepository integrationEntityRepository;
  @Mock private OutboxService outboxService;
  @Mock private TransactionHelper transactionHelper;
  @Mock private HarnessCDIntegrationOpsImpl harnessCDIntegrationOps;
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private KindEntityRepository kindEntityRepository;
  @Mock private CatalogServiceHelper catalogServiceHelper;
  @Mock private CatalogService catalogService;
  @Mock private IDPToHarnessHelper idpToHarnessHelper;
  @Mock private io.harness.idp.catalog.helpers.IDPGitXHelper idpGitXHelper;
  @Mock private IntegrationManagerClientHelper integrationManagerClientHelper;
  @Mock private IdpCommonService idpCommonService;
  @Mock private SetupUsageProducer setupUsageProducer;
  @Mock private Call<TypesIntegrationConfig> integrationConfigCall;
  @Mock private Call<EntityMappedEntityResponseObject> mappedEntitiesCall;
  @Mock private Call<EntitySubscribeEntitiesResponse> subscribeCall;

  @InjectMocks private CatalogIntegrationServiceImpl catalogIntegrationService;

  private HarnessCDIntegrationEntity testEntity;
  private HarnessCDIntegrationRequest testRequest;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      Object arg = invocation.getArgument(0);
      if (arg instanceof Supplier) {
        return ((Supplier<?>) arg).get();
      }
      // TransactionHelper.TransactionFunction exposes execute(), so the body must be run through it -
      // looking for get() silently swallowed every transactional write in these tests.
      if (arg instanceof TransactionHelper.TransactionFunction) {
        return ((TransactionHelper.TransactionFunction<?>) arg).execute();
      }
      return null;
    });

    List<String> scopes = Arrays.asList("account", "org1", "project1");

    testEntity = HarnessCDIntegrationEntity.builder()
                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                     .identifier(TEST_IDENTIFIER)
                     .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                     .integration(IntegrationEntity.Integration.CATALOG)
                     .scopesToSync(String.join(",", scopes))
                     .enabled(true)
                     .autoDeletion(true) // HarnessCDIntegrationOpsImpl always sets this to true
                     .build();

    testRequest = new HarnessCDIntegrationRequest();
    testRequest.setCatalogIntegrationType(CatalogIntegrationRequest.CatalogIntegrationTypeEnum.HARNESS_CD);
    testRequest.setEnabled(true);
    testRequest.setScopes(String.join(",", scopes));
    testRequest.setAutoDeletion(false);

    Map<String, Object> requestData = new HashMap<>();
    requestData.put("enabled", true);
    requestData.put("scopes", String.join(",", scopes));
    requestData.put("auto_deletion", false);
    testRequest.setCatalogIntegrationRequest(requestData);

    when(catalogServiceHelper.queryableEntityRef(any(CatalogEntity.class))).thenReturn("component:account/test");
    when(catalogService.getEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), any(), eq(false), eq(false), eq(true)))
        .thenReturn(new EntityResponse().entityRef("component:account/test-entity"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetByIdentifier() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, IntegrationEntity.Integration.CATALOG))
        .thenReturn(Optional.of(testEntity));

    CatalogIntegrationResponse response = catalogIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER);

    assertThat(response).isNotNull();
    verify(integrationEntityRepository, times(1))
        .findByAccountIdentifierAndIdentifierAndIntegration(
            TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, IntegrationEntity.Integration.CATALOG);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetByIdentifierNotFound() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, IntegrationEntity.Integration.CATALOG))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> catalogIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetList() {
    Pageable pageable = PageRequest.of(0, 10);
    List<IntegrationEntity> entities = Collections.singletonList(testEntity);
    Page<IntegrationEntity> page = new PageImpl<>(entities, pageable, 1);

    when(integrationEntityRepository.findAll(any(Criteria.class), eq(pageable))).thenReturn(page);

    List<CatalogIntegrationResponse> responses = catalogIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, pageable, null);

    assertThat(responses).hasSize(1);
    verify(integrationEntityRepository, times(1)).findAll(any(Criteria.class), eq(pageable));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, IntegrationEntity.Integration.CATALOG))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> catalogIntegrationService.delete(TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_MatchesInDecorator() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    service_now_id: \"SYS123456\"");
    entityResponse.setYaml("apiVersion: harness.io/v1\nkind: component\nmetadata:\n  name: test");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "SYS123456");

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_MatchesInYaml() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    other_field: \"other_value\"");
    entityResponse.setYaml(
        "apiVersion: harness.io/v1\nkind: component\nmetadata:\n  annotations:\n    service_now_id: \"SYS123456\"");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "SYS123456");

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_NoMatch() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    service_now_id: \"DIFFERENT_VALUE\"");
    entityResponse.setYaml(
        "apiVersion: harness.io/v1\nkind: component\nmetadata:\n  annotations:\n    service_now_id: \"ANOTHER_VALUE\"");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "SYS123456");

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_PathNotFound() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  name: test");
    entityResponse.setYaml("apiVersion: harness.io/v1\nkind: component\nmetadata:\n  name: test");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "SYS123456");

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_NullEntityResponse() {
    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        null, ".metadata.annotations.service_now_id", "SYS123456");

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_BlankCorrelationField() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(entityResponse, "", "SYS123456");

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_BlankCorrelationValue() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    service_now_id: \"SYS123456\"");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "");

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_PathWithoutLeadingDot() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    service_now_id: \"SYS123456\"");
    entityResponse.setYaml("apiVersion: harness.io/v1\nkind: component");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, "metadata.annotations.service_now_id", "SYS123456");

    assertThat(result).isTrue();
  }

  // ---- CatalogInfo handler tests ----

  private static final String BACKSTAGE_YAML =
      "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: test_repo\nspec:\n  type: service\n";
  // convertBackstageToHarness serializes through CatalogMapper.presentationYaml, which writes kind in
  // display casing (Component / Workflow / API) while the catalog stores and queries it lowercase. The
  // fixtures keep that casing so the handler's lookups are exercised the way production sees them.
  private static final String HARNESS_YAML =
      "apiVersion: harness.io/v1\nkind: Component\nidentifier: test_repo\nname: test_repo\ntype: service\n";

  private EntityMappedEntityResponse buildCatalogInfoResponse() {
    EntityMappedEntityResponse response = buildEntityMappedEntityResponse("uuid-ci", "catalog_info_file", "test_repo");
    response.setName("test_repo");
    response.getData().put("content", BACKSTAGE_YAML);
    response.getData().put("kind", "component");
    response.getData().put("repo", "harness/source-repo");
    response.getData().put("branch", "feature/catalog");
    return response;
  }

  private void stubCatalogInfoLookupDefaults() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                              .orgIdentifier("testOrg")
                              .projectIdentifier("testProject")
                              .uniqueId("test-parent-unique-id")
                              .build();
    when(catalogServiceHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(Collections.emptyList());
  }

  private CatalogEntity buildSavedCatalogInfoEntity() {
    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier("test_repo")
        .kind(COMPONENT_KIND)
        .parentUniqueId("test-parent-unique-id")
        .build();
  }

  private TypesIntegrationConfig buildCatalogInfoConfig(boolean gitSyncEnabled) {
    TypesIntegrationConfig config = new TypesIntegrationConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.CatalogInfo);
    config.setIntegrationMode(TypesIntegrationConfig.IntegrationMode.airbyte);
    config.setIdentifier("catalog_info_integration");
    config.setSpacePath(TEST_ACCOUNT_IDENTIFIER);
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("git_sync_enabled", gitSyncEnabled);
    if (gitSyncEnabled) {
      configuration.put("git_sync_connector_ref", "account.write_connector");
      configuration.put("sync_repo", "harness/idp-catalog");
      configuration.put("sync_branch", "main");
      configuration.put("sync_base_path", "/.harness/idp");
    }
    config.setConfiguration(configuration);
    return config;
  }

  // Scopes are pre-resolved once per batch in saveDiscoverEntitiesInternal, so the handler receives them.
  private List<ScopeInfo> catalogInfoScopeInfos(String orgIdentifier, String projectIdentifier) {
    return Collections.singletonList(ScopeInfo.builder()
                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .uniqueId("test-parent-unique-id")
                                         .build());
  }

  private boolean invokeHandleCatalogInfoEntity(EntityMappedEntityResponse entity, TypesIntegrationConfig config) {
    return invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject");
  }

  // handleCatalogInfoEntity is package-private, so the test (same package) calls it directly - no reflection.
  private boolean invokeHandleCatalogInfoEntity(EntityMappedEntityResponse entity, TypesIntegrationConfig config,
      String orgIdentifier, String projectIdentifier) {
    return invokeHandleCatalogInfoEntity(entity, config, orgIdentifier, projectIdentifier, null, null, null);
  }

  private boolean invokeHandleCatalogInfoEntity(EntityMappedEntityResponse entity, TypesIntegrationConfig config,
      String orgIdentifier, String projectIdentifier, String actionDestination,
      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action) {
    return invokeHandleCatalogInfoEntity(
        entity, config, orgIdentifier, projectIdentifier, actionDestination, null, action);
  }

  private boolean invokeHandleCatalogInfoEntity(EntityMappedEntityResponse entity, TypesIntegrationConfig config,
      String orgIdentifier, String projectIdentifier, String actionDestination, String actionIdentifier,
      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action) {
    return catalogIntegrationService.handleCatalogInfoEntity(entity, TEST_ACCOUNT_IDENTIFIER, orgIdentifier,
        projectIdentifier, config, "catalog_info_integration", actionDestination, actionIdentifier, action,
        catalogInfoScopeInfos(orgIdentifier, projectIdentifier));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterNewEntityGitSyncOff() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService, times(1))
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    // name override from mapped-entity name keeps identifier/name as test_repo
    assertThat(createCaptor.getValue().getYaml()).contains("identifier: test_repo");
    assertThat(createCaptor.getValue().getYaml()).contains("name: test_repo");
    assertThat(createCaptor.getValue().getGitDetails()).isNull();
  }

  // Org-scoped entity: the org is passed into conversion (so the converted YAML carries it) AND into
  // createEntity, so the scope param matches the YAML scope (no "Mismatch ... query param and YAML" error).
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterOrgScopedEntity() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq(""), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq(""), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq(""), eq(false), eq(false),
             any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);

    invokeHandleCatalogInfoEntity(entity, config, "testOrg", "");

    verify(idpToHarnessHelper, times(1))
        .convertBackstageToHarness(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq(""), eq(BACKSTAGE_YAML));
    verify(catalogService, times(1))
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq(""), eq(false), eq(false), any());
  }

  // Project-scoped entity: both org and project flow through conversion and create.
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterProjectScopedEntity() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);

    invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject");

    verify(idpToHarnessHelper, times(1))
        .convertBackstageToHarness(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML));
    verify(catalogService, times(1))
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterNewEntityGitSyncOn() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService, times(1))
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    EntityCreateRequest create = createCaptor.getValue();
    assertThat(create.getYaml()).contains("identifier: test_repo");
    assertThat(create.getGitDetails()).isNull();

    ArgumentCaptor<EntityMoveRequest> moveCaptor = ArgumentCaptor.forClass(EntityMoveRequest.class);
    verify(catalogService, times(1))
        .moveEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), any(String.class), moveCaptor.capture());
    EntityMoveRequest moveReq = moveCaptor.getValue();
    assertThat(moveReq.getEntityMoveOperationType())
        .isEqualTo(io.harness.spec.server.idp.v1.model.EntityMoveOperationType.INLINE_TO_REMOTE);
    assertThat(moveReq.getGitDetails().getConnectorRef()).isEqualTo("account.write_connector");
    assertThat(moveReq.getGitDetails().getRepoName()).isEqualTo("harness/idp-catalog");
    assertThat(moveReq.getGitDetails().getBranchName()).isEqualTo("main");
    assertThat(moveReq.getGitDetails().getFilePath())
        .isEqualTo(".harness/idp/component/orgs/testOrg/projects/testProject/test_repo.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoGitSyncToSourceRepo() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    config.getConfiguration().put("sync_to_source_repo", true);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);

    invokeHandleCatalogInfoEntity(entity, config);

    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService, times(1))
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    EntityCreateRequest create = createCaptor.getValue();
    assertThat(create.getGitDetails()).isNull();

    ArgumentCaptor<EntityMoveRequest> moveCaptor = ArgumentCaptor.forClass(EntityMoveRequest.class);
    verify(catalogService, times(1))
        .moveEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), any(String.class), moveCaptor.capture());
    EntityMoveRequest moveReq = moveCaptor.getValue();
    // Airbyte emits owner/repo; GitX connectors need the bare repo name — not the configured sync_repo.
    assertThat(moveReq.getGitDetails().getRepoName()).isEqualTo("source-repo");
    // branch comes from the entity's source branch, NOT the configured sync_branch (main).
    assertThat(moveReq.getGitDetails().getBranchName()).isEqualTo("feature/catalog");
    assertThat(moveReq.getGitDetails().getFilePath())
        .isEqualTo(".harness/idp/component/orgs/testOrg/projects/testProject/test_repo.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoGitSyncToSourceRepoMissingBranchStaysInline() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    // Source branch is nullable from IM, and sync_branch belongs to the (unused) sync_repo.
    entity.getData().remove("branch");
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    config.getConfiguration().put("sync_to_source_repo", true);
    config.getConfiguration().put("sync_branch", "develop");
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class));
    verify(catalogService, never()).moveEntity(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoSkipWhenInlineEntityExists() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(buildMappedEntity("test_repo"));

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper, times(1)).convertBackstageToHarness(any(), any(), any(), any());
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    verify(integrationManagerClientHelper, never()).subscribeToEntityUpdates(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoSkipWhenGitReferencedEntityExists() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    stubCatalogInfoLookupDefaults();

    GitReferencedCatalogEntity gitEntity = GitReferencedCatalogEntity.builder()
                                               .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                               .identifier("test_repo")
                                               .kind(COMPONENT_KIND)
                                               .build();
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(gitEntity);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper, times(1)).convertBackstageToHarness(any(), any(), any(), any());
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    verify(integrationManagerClientHelper, never()).subscribeToEntityUpdates(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoConversionFailureSkipsEntity() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenThrow(new InvalidRequestException("bad yaml"));

    // must not throw out of the handler (outer forEach loop relies on this for ack)
    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isFalse();
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    verify(integrationManagerClientHelper, never()).subscribeToEntityUpdates(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoCreateDuplicateNotSwallowedByHandler() throws Exception {
    // TOCTOU: lookup says absent but createEntity throws duplicate. The exception propagates (not
    // swallowed) so the forEach catch logs it; no boolean is returned and subscription does not fire.
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null);
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenThrow(new InvalidRequestException("Entity with identifier [test_repo] already exists for the same kind"));

    assertThatThrownBy(() -> invokeHandleCatalogInfoEntity(entity, config)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterGitSyncFailureStillRegisters() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(io.harness.spec.server.idp.v1.model.EntityCreateRequest.class)))
        .thenReturn(created);
    doThrow(new InvalidRequestException("git push failed"))
        .when(catalogService)
        .moveEntity(any(), any(), any(), any(), any());

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    verify(catalogService, times(1)).createEntity(any(), any(), any(), any(), any(), any());
    verify(catalogService, times(1)).moveEntity(any(), any(), any(), any(), any());
  }

  // ---- saveDiscoverEntitiesInternal subscription wiring tests ----

  private void stubSaveDiscoverEntitiesInternal(TypesIntegrationConfig config, EntityMappedEntityResponse entity)
      throws Exception {
    stubSaveDiscoverEntitiesInternal(config, List.of(entity));
  }

  private void stubSaveDiscoverEntitiesInternal(
      TypesIntegrationConfig config, List<EntityMappedEntityResponse> entities) throws Exception {
    when(integrationManagerClientHelper.getIntegrationConfig(any(), any(), any(), any(), any()))
        .thenReturn(integrationConfigCall);
    when(integrationManagerClientHelper.updateIntegrationConfig(any(), any(), any(), any(), any(), any()))
        .thenReturn(integrationConfigCall);
    when(integrationConfigCall.execute()).thenReturn(Response.success(config));

    EntityMappedEntityResponseObject responseObject = new EntityMappedEntityResponseObject(entities);
    // Provide X-Page > X-Total-Pages so the do-while pagination loop exits after one call.
    okhttp3.Headers paginationHeaders =
        new okhttp3.Headers.Builder().add("X-Page", "1").add("X-Total-Pages", "0").build();
    when(integrationManagerClientHelper.getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(),
             any(), anyInt(), anyInt(), any(), any(), anyBoolean()))
        .thenReturn(mappedEntitiesCall);
    when(mappedEntitiesCall.execute()).thenReturn(Response.success(responseObject, paginationHeaders));
    when(integrationManagerClientHelper.getIntegrationManagerIdpMappingId()).thenReturn("idp-mapping-id");

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(any(), any(), any()))
        .thenReturn(Pair.of(List.of(ScopeInfo.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .orgIdentifier("testOrg")
                                        .projectIdentifier("testProject")
                                        .uniqueId("test-parent-unique-id")
                                        .build()),
            Collections.emptyMap()));
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveDiscoverEntitiesInternalSubscribesWhenEntityRegistered() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);

    stubSaveDiscoverEntitiesInternal(config, entity);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);
    when(integrationManagerClientHelper.subscribeToEntityUpdates(any(), any(), any(), any(), any(), any()))
        .thenReturn(subscribeCall);
    when(subscribeCall.execute()).thenReturn(Response.success(new EntitySubscribeEntitiesResponse()));

    UserPrincipal userPrincipal =
        UserPrincipal.newBuilder().setUuid("user-uuid").setName("Test User").setEmail("test@harness.io").build();
    SaveDiscoverEntitiesRequest request = buildSaveAllRequest(SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL);

    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, "", "", "catalog_info_integration", request, userPrincipal);

    verify(integrationManagerClientHelper, times(1)).subscribeToEntityUpdates(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveDiscoverEntitiesInternalDoesNotSubscribeWhenEntitySkipped() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);

    stubSaveDiscoverEntitiesInternal(config, entity);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    // entity already exists — handleCatalogInfoEntity returns false
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(buildSavedCatalogInfoEntity());

    UserPrincipal userPrincipal =
        UserPrincipal.newBuilder().setUuid("user-uuid").setName("Test User").setEmail("test@harness.io").build();
    SaveDiscoverEntitiesRequest request = buildSaveAllRequest(SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL);

    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, "", "", "catalog_info_integration", request, userPrincipal);

    verify(integrationManagerClientHelper, never()).subscribeToEntityUpdates(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterUsesActionDestinationForNameAndIdentifier() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("jdoe23")))
        .thenReturn(null)
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("jdoe23")
                        .kind(COMPONENT_KIND)
                        .parentUniqueId("test-parent-unique-id")
                        .build());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("jdoe23");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(EntityCreateRequest.class)))
        .thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", "jdoe-23",
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    assertThat(createCaptor.getValue().getYaml()).contains("name: jdoe-23");
    assertThat(createCaptor.getValue().getYaml()).contains("identifier: jdoe23");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterUsesActionIdentifierWhenPresent() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("custom_id")))
        .thenReturn(null)
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("custom_id")
                        .kind(COMPONENT_KIND)
                        .parentUniqueId("test-parent-unique-id")
                        .build());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("custom_id");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(EntityCreateRequest.class)))
        .thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", "jdoe-23", "custom_id",
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    assertThat(createCaptor.getValue().getYaml()).contains("name: jdoe-23");
    assertThat(createCaptor.getValue().getYaml()).contains("identifier: custom_id");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterUsesActionIdentifierWhenNameBlank() throws Exception {
    // actionIdentifier must apply even when action_destination and mapped-entity name are blank;
    // previously the blank-name guard returned before reading actionIdentifier.
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.setName(null);
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("custom_id")))
        .thenReturn(null)
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("custom_id")
                        .kind(COMPONENT_KIND)
                        .parentUniqueId("test-parent-unique-id")
                        .build());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("custom_id");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(EntityCreateRequest.class)))
        .thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, "custom_id",
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    String yaml = createCaptor.getValue().getYaml();
    assertThat(yaml).contains("identifier: custom_id");
    // Name left as converter-derived when no destination/mapped name was supplied.
    assertThat(yaml).contains("name: test_repo");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoMergeActionIsRejected() {
    // CatalogInfo is register-only. Explicit MERGE must not fall through to createEntity.
    // Redis updates leave action null and are unaffected.
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    assertThatThrownBy(
        ()
            -> invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", "component:account.org.proj/foo",
                SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Action MERGE is not allowed");
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAutoDiscoverKeepsConvertedNameAndIdentifier() throws Exception {
    // No UI rename (no action_destination / action_identifier): the converted YAML must be created
    // verbatim. Re-deriving the identifier from the Integration Manager name would diverge from the
    // converter's hyphen-to-underscore mapping used by relation refs.
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.setName("My Mapped Name");
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    assertThat(createCaptor.getValue().getYaml()).isEqualTo(HARNESS_YAML);
    verify(catalogServiceHelper, never())
        .catalogEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("My_Mapped_Name"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoHyphenatedNameUsesConvertedIdentifierForLookup() throws Exception {
    String backstageYaml =
        "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: my-service\nspec:\n  type: service\n";
    String harnessYaml =
        "apiVersion: harness.io/v1\nkind: Component\nidentifier: my_service\nname: my-service\ntype: service\n";
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.setName("my-service");
    entity.getData().put("content", backstageYaml);
    entity.getData().put("identifier", "my-service"); // IM identifier still hyphenated
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(backstageYaml)))
        .thenReturn(harnessYaml);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("my_service")))
        .thenReturn(null)
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("my_service")
                        .kind(COMPONENT_KIND)
                        .parentUniqueId("test-parent-unique-id")
                        .build());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("my_service");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    // Lookups use the converter's identifier (hyphens to underscores, matching relation refs), never the
    // hyphenated Integration Manager identifier
    verify(catalogServiceHelper, times(2))
        .catalogEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("my_service"));
    verify(catalogServiceHelper, never())
        .catalogEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("my-service"));
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    assertThat(createCaptor.getValue().getYaml()).isEqualTo(harnessYaml);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoTemplateKindUsesWorkflowForLookupAndDecorate() throws Exception {
    String backstageTemplateYaml =
        "apiVersion: scaffolder.backstage.io/v1beta3\nkind: Template\nmetadata:\n  name: my_template\n";
    String harnessWorkflowYaml =
        "apiVersion: harness.io/v1\nkind: Workflow\nidentifier: my_template\nname: my_template\n";
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.setName("my_template");
    entity.getData().put("content", backstageTemplateYaml);
    entity.getData().put("kind", "template"); // IM kind may differ from converted YAML
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(backstageTemplateYaml)))
        .thenReturn(harnessWorkflowYaml);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("workflow"), eq("my_template")))
        .thenReturn(null)
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("my_template")
                        .kind("workflow")
                        .parentUniqueId("test-parent-unique-id")
                        .build());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("my_template");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isTrue();
    // The catalog stores kind lowercase, so the converter's display casing must be sanitized before the
    // existence check and the post-create re-fetch. A "Workflow" lookup misses, and the entity is then
    // created but never decorated or subscribed - it stays on the discovered tab forever.
    verify(catalogServiceHelper, times(2))
        .catalogEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("workflow"), eq("my_template"));
    verify(catalogServiceHelper, never())
        .catalogEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("Workflow"), any());
    verify(catalogServiceHelper, never())
        .catalogEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("template"), any());
    verify(catalogEntityRepository).save(any(CatalogEntity.class));
    // createEntity keeps the converter's YAML verbatim - it sanitizes the kind itself.
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    assertThat(createCaptor.getValue().getYaml()).isEqualTo(harnessWorkflowYaml);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAlreadyLinkedGitSyncEnabledSkipsUpdate() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    stubCatalogInfoLookupDefaults();

    CatalogEntity linked = buildMappedEntity("test_repo");
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, null);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper, never()).convertBackstageToHarness(any(), any(), any(), any());
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    verify(catalogService, never())
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    verify(catalogService, never()).moveEntity(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAlreadyLinkedGitReferencedSkipsUpdateEvenWhenGitSyncDisabled() throws Exception {
    // Git-referenced linked entity + git_sync_enabled=false must still skip redis refresh.
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    // Config flag off, but the linked entity itself is still git-backed (registered while sync was on,
    // or moved to git manually). Background redis refresh must not write converted YAML to the repo.
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    CatalogEntity linked = GitReferencedCatalogEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                               .orgIdentifier("testOrg")
                               .projectIdentifier("testProject")
                               .identifier("test_repo")
                               .name("Original Name")
                               .kind(COMPONENT_KIND)
                               .parentUniqueId("test-parent-unique-id")
                               .yaml(HARNESS_YAML)
                               .repo("harness/idp-catalog")
                               .connectorRef("account.write_connector")
                               .repoURL("https://github.com/harness/idp-catalog")
                               .build();
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, null);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper, never()).convertBackstageToHarness(any(), any(), any(), any());
    verify(catalogService, never())
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    verify(catalogEntityRepository, never()).save(any(CatalogEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAlreadyLinkedGitSyncDisabledMergesAndPreservesIdentifier() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();
    String convertedUpdateYaml =
        "apiVersion: harness.io/v1\nkind: Component\nidentifier: changed_identifier\nname: Updated From Backstage\n"
        + "type: service\n";

    CatalogEntity linked = InlineCatalogEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                               .orgIdentifier("testOrg")
                               .projectIdentifier("testProject")
                               .identifier("test_repo")
                               .name("Original Name")
                               .kind(COMPONENT_KIND)
                               .parentUniqueId("test-parent-unique-id")
                               .yaml(HARNESS_YAML)
                               .build();
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));
    when(catalogServiceHelper.queryableEntityRef(any(CatalogEntity.class))).thenReturn("component:account/test_repo");
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(convertedUpdateYaml);

    EntityResponse getEntityResponse = new EntityResponse();
    getEntityResponse.setYaml(HARNESS_YAML);
    when(catalogService.getEntity(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(getEntityResponse);
    when(catalogService.updateEntity(
             any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(getEntityResponse);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, null);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper)
        .convertBackstageToHarness(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML));
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    ArgumentCaptor<EntityUpdateRequest> updateCaptor = ArgumentCaptor.forClass(EntityUpdateRequest.class);
    verify(catalogService)
        .updateEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), any(), updateCaptor.capture(),
            eq(false), eq(true), eq(false), eq(false));
    assertThat(updateCaptor.getValue().getYaml()).contains("identifier: test_repo");
    assertThat(updateCaptor.getValue().getYaml()).contains("name: Updated From Backstage");
    assertThat(updateCaptor.getValue().getYaml()).doesNotContain("changed_identifier");
    // Decorate saves the whole pre-update document under the same id, so it must run before the YAML
    // write - otherwise it replays the stale name/yaml over the refresh.
    ArgumentCaptor<CatalogEntity> saveCaptor = ArgumentCaptor.forClass(CatalogEntity.class);
    InOrder inOrder = inOrder(catalogEntityRepository, catalogService);
    inOrder.verify(catalogEntityRepository).save(saveCaptor.capture());
    inOrder.verify(catalogService)
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    // CatalogInfo is register-only: Redis refresh must keep entity_action=REGISTER so the Imported
    // Entities API does not flip action_performed to MERGE.
    Map<String, Object> decorator = saveCaptor.getValue().getDecorator();
    Map<String, Object> processedData = (Map<String, Object>) decorator.get(PROCESSED_DATA);
    Map<String, Object> metadata = (Map<String, Object>) processedData.get("metadata");
    Map<String, Object> integration = (Map<String, Object>) metadata.get("integration");
    // normalizeSpacePath rewrites the account id to the literal "account"
    Map<String, Object> space = (Map<String, Object>) integration.get("account");
    Map<String, Object> integrationId = (Map<String, Object>) space.get("catalog_info_integration");
    Map<String, Object> linkage = (Map<String, Object>) integrationId.get("catalog_info_file");
    assertThat(linkage.get("entity_action")).isEqualTo("REGISTER");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoYamlContentEquivalentIgnoresKindCasing() {
    String displayCase =
        "apiVersion: harness.io/v1\nkind: Component\nidentifier: test_repo\nname: test_repo\ntype: service\n";
    String lowerCase =
        "apiVersion: harness.io/v1\nkind: component\nidentifier: test_repo\nname: test_repo\ntype: service\n";

    assertThat(catalogIntegrationService.catalogInfoYamlContentEquivalent(displayCase, lowerCase)).isTrue();
    assertThat(catalogIntegrationService.catalogInfoYamlContentEquivalent(lowerCase, displayCase)).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAlreadyLinkedSkipsDecorateAndUpdateWhenYamlUnchanged() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    CatalogEntity linked = InlineCatalogEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                               .orgIdentifier("testOrg")
                               .projectIdentifier("testProject")
                               .identifier("test_repo")
                               .kind(COMPONENT_KIND)
                               .parentUniqueId("test-parent-unique-id")
                               .yaml(HARNESS_YAML)
                               .build();
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, null);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper)
        .convertBackstageToHarness(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML));
    verify(catalogEntityRepository, never()).save(any(CatalogEntity.class));
    verify(catalogService, never()).getEntity(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    verify(catalogService, never())
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAlreadyLinkedSkipsUpdateWhenConvertedKindChanges() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();
    // catalog-info.yaml became a Template, which converts to a different Harness kind than the linked entity.
    String convertedWorkflowYaml =
        "apiVersion: harness.io/v1\nkind: Workflow\nidentifier: test_repo\nname: test_repo\ntype: service\n";

    CatalogEntity linked = InlineCatalogEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                               .orgIdentifier("testOrg")
                               .projectIdentifier("testProject")
                               .identifier("test_repo")
                               .name("Original Name")
                               .kind(COMPONENT_KIND)
                               .parentUniqueId("test-parent-unique-id")
                               .yaml(HARNESS_YAML)
                               .build();
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(convertedWorkflowYaml);

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, null);

    assertThat(registered).isFalse();
    // updateEntity would reject the kind mismatch against the entity ref, so skip explicitly instead.
    verify(catalogService, never())
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
    verify(catalogEntityRepository, never()).save(any(CatalogEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoAlreadyLinkedSkipsUpdateWhenConversionFails() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    // Drifted catalog-info that convertBackstageToHarness would reject (e.g. missing spec.owner).
    entity.getData().put("content", "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: broken\n");
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    CatalogEntity linked = InlineCatalogEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                               .orgIdentifier("testOrg")
                               .projectIdentifier("testProject")
                               .identifier("test_repo")
                               .name("Original Name")
                               .kind(COMPONENT_KIND)
                               .parentUniqueId("test-parent-unique-id")
                               .yaml(HARNESS_YAML)
                               .build();
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));
    when(idpToHarnessHelper.convertBackstageToHarness(any(), any(), any(), any()))
        .thenThrow(new InvalidRequestException("owner cannot be null or empty for kind as api / component / resource"));

    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", null, null);

    assertThat(registered).isFalse();
    verify(idpToHarnessHelper)
        .convertBackstageToHarness(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), any());
    verify(catalogService, never())
        .updateEntity(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoManualRegisterWhenAlreadyLinkedThrows() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    CatalogEntity linked = buildMappedEntity("test_repo");
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));

    assertThatThrownBy(()
                           -> invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", "jdoe-23",
                               SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("already linked");
    verify(idpToHarnessHelper, never()).convertBackstageToHarness(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveDiscoverEntitiesInternalLogsAndContinuesOnRegisterAlreadyLinked() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubSaveDiscoverEntitiesInternal(config, entity);
    stubCatalogInfoLookupDefaults();

    CatalogEntity linked = buildMappedEntity("test_repo");
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(List.of(linked));

    SaveDiscoverEntitiesRequest request = buildSaveIndividualRequest(
        entity.getUuid(), SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER, "jdoe-23", null);

    // Same as other integrations: InvalidRequestException is logged and skipped by the batch loop.
    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, "", "", "catalog_info_integration", request, null);

    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveDiscoverEntitiesInternalReusesPreResolvedScopeInfos() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubSaveDiscoverEntitiesInternal(config, entity);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    SaveDiscoverEntitiesRequest request = buildSaveAllRequest(SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL);
    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, "", "", "catalog_info_integration", request, null);

    // Scope infos come from the enclosing cache/allScopeInfos path, not a per-entity getScopeInfo.
    verify(catalogServiceHelper, never()).getScopeInfo(any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testSaveDiscoverEntitiesInternalFirstTimeRegisterInFreshScopeDoesNotScanAccount() throws Exception {
    // Entity is in a scope with no existing catalog entities, so it is absent from allScopeInfos
    // (which is derived from existing entities). The linkage lookup must resolve to an empty scope
    // list - a scope with no entities cannot hold a linkage - not widen back to the whole account.
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.getScope().setOrgIdentifier("freshOrg");
    entity.getScope().setProjectIdentifier("freshProject");
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubSaveDiscoverEntitiesInternal(config, entity);
    // allScopeInfos contains only testOrg/testProject; freshOrg/freshProject is not present.
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(Collections.emptyList());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("freshOrg"), eq("freshProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("freshOrg"), eq("freshProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    SaveDiscoverEntitiesRequest request = buildSaveIndividualRequest(
        entity.getUuid(), SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);
    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, "", "", "catalog_info_integration", request, null);

    ArgumentCaptor<List<String>> parentUniqueIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(catalogEntityRepository).getEntitiesFilters(parentUniqueIdsCaptor.capture(), any(), any());
    // No parentUniqueId => query matches nothing, i.e. no full-account scan on the first-register path.
    assertThat(parentUniqueIdsCaptor.getValue()).isEmpty();
    verify(catalogService).createEntity(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoRegisterNameWithoutUsableIdentifierKeepsConvertedIdentifier() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    // "123" sanitizes to an empty identifier: the rename still applies, only the identifier is kept.
    boolean registered = invokeHandleCatalogInfoEntity(entity, config, "testOrg", "testProject", "123",
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);

    assertThat(registered).isTrue();
    ArgumentCaptor<EntityCreateRequest> createCaptor = ArgumentCaptor.forClass(EntityCreateRequest.class);
    verify(catalogService)
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false),
            createCaptor.capture());
    Map<String, Object> createdYaml = YamlUtils.loadYamlStringAsMap(createCaptor.getValue().getYaml());
    assertThat(createdYaml.get("identifier")).isEqualTo("test_repo");
    assertThat(String.valueOf(createdYaml.get("name"))).isEqualTo("123");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoMalformedConvertedYamlSkipsEntity() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn("kind: component\n\tidentifier: bad-tab\n");

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isFalse();
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoEmptyConvertedYamlSkipsEntity() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    stubCatalogInfoLookupDefaults();

    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn("   ");

    boolean registered = invokeHandleCatalogInfoEntity(entity, config);

    assertThat(registered).isFalse();
    verify(catalogService, never()).createEntity(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCatalogInfoGitSyncToSourceRepoKeepsGitlabSubgroup() throws Exception {
    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    // Self-hosted GitLab emits group/subgroup/project; only the owner segment may be stripped.
    entity.getData().put("repo", "group/subgroup/project");
    TypesIntegrationConfig config = buildCatalogInfoConfig(true);
    config.getConfiguration().put("sync_to_source_repo", true);
    stubCatalogInfoLookupDefaults();

    when(catalogServiceHelper.catalogEntity(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq("component"), eq("test_repo")))
        .thenReturn(null)
        .thenReturn(buildSavedCatalogInfoEntity());
    when(idpToHarnessHelper.convertBackstageToHarness(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(BACKSTAGE_YAML)))
        .thenReturn(HARNESS_YAML);
    EntityResponse created = new EntityResponse();
    created.setIdentifier("test_repo");
    when(catalogService.createEntity(any(), any(), any(), any(), any(), any())).thenReturn(created);

    invokeHandleCatalogInfoEntity(entity, config);

    ArgumentCaptor<EntityMoveRequest> moveCaptor = ArgumentCaptor.forClass(EntityMoveRequest.class);
    verify(catalogService, times(1))
        .moveEntity(
            eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), any(String.class), moveCaptor.capture());
    assertThat(moveCaptor.getValue().getGitDetails().getRepoName()).isEqualTo("subgroup/project");
  }

  // ---- Helper methods for processEntity tests ----

  private CatalogEntity buildMappedEntity(String identifier) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(identifier)
        .kind(COMPONENT_KIND)
        .build();
  }

  private EntityMappedEntityResponse buildEntityMappedEntityResponse(String uuid, String kind, String identifier) {
    EntityMappedEntityResponse.EntityEntityScope scope = new EntityMappedEntityResponse.EntityEntityScope();
    scope.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    scope.setOrgIdentifier("testOrg");
    scope.setProjectIdentifier("testProject");

    EntityMappedEntityResponse.EntityEntityIdentifierInfoType2 entityInfo =
        new EntityMappedEntityResponse.EntityEntityIdentifierInfoType2();
    entityInfo.setIdentifier(identifier);

    Map<String, Object> data = new HashMap<>();
    data.put("kind", kind);
    data.put("type", "service");
    data.put("identifier", identifier);

    EntityMappedEntityResponse response = new EntityMappedEntityResponse();
    response.setUuid(uuid);
    response.setKind(kind);
    response.setName("Test Entity");
    response.setScope(scope);
    response.setEntityInfo(entityInfo);
    response.setData(data);
    return response;
  }

  private TypesIntegrationConfig buildIntegrationConfig() {
    TypesIntegrationConfig config = new TypesIntegrationConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.PagerDuty);
    config.setIntegrationMode(TypesIntegrationConfig.IntegrationMode.airbyte);
    return config;
  }

  private SaveDiscoverEntitiesRequest buildSaveAllRequest(
      SaveDiscoverEntitiesRequest.SelectionFilterEnum selectionFilterEnum) {
    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest();
    request.setSelectionFilter(selectionFilterEnum);
    request.setIntegrationEntities(Collections.emptyList());
    return request;
  }

  private SaveDiscoverEntitiesRequest buildSaveIndividualRequest(
      String entityUuid, SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action) {
    return buildSaveIndividualRequest(entityUuid, action, null, null);
  }

  private SaveDiscoverEntitiesRequest buildSaveIndividualRequest(String entityUuid,
      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action, String actionDestination,
      String actionIdentifier) {
    SaveDiscoverEntitiesRequestIntegrationEntities entity = new SaveDiscoverEntitiesRequestIntegrationEntities();
    entity.setIntegrationEntityId(entityUuid);
    entity.setAction(action);
    if (actionDestination != null) {
      entity.setActionDestination(actionDestination);
    }
    if (actionIdentifier != null) {
      entity.setActionIdentifier(actionIdentifier);
    }
    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest();
    request.setSelectionFilter(SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL);
    request.setIntegrationEntities(List.of(entity));
    return request;
  }

  // ---- processEntityRegisterOnly tests ----

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityRegisterOnly_AllTrue_MappedEntity_ThrowsException() {
    CatalogEntity mappedEntity = buildMappedEntity("existing-entity");
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request = buildSaveAllRequest(SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL);
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityRegisterOnly(request, entityMappedEntityResponse, "uuid-123",
                mappedEntity, null, TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject", "services", "service",
                "test-entity", "linkagePath", Collections.emptyMap(), config, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot register")
        .hasMessageContaining("already linked");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityRegisterOnly_AllFalse_ActionMerge_ThrowsException() {
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request =
        buildSaveIndividualRequest("uuid-123", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE);
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(()
                           -> catalogIntegrationService.processEntityRegisterOnly(request, entityMappedEntityResponse,
                               "uuid-123", null, null, TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject", "services",
                               "service", "test-entity", "linkagePath", Collections.emptyMap(), config, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Action MERGE is not allowed")
        .hasMessageContaining("restricts action to Register");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityRegisterOnly_AllFalse_MappedEntity_ThrowsException() {
    CatalogEntity mappedEntity = buildMappedEntity("existing-entity");
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request =
        buildSaveIndividualRequest("uuid-123", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityRegisterOnly(request, entityMappedEntityResponse, "uuid-123",
                mappedEntity, null, TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject", "services", "service",
                "test-entity", "linkagePath", Collections.emptyMap(), config, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot register")
        .hasMessageContaining("already linked");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityRegisterOnly_AllFalse_WithActionIdentifier_MappedEntity_ThrowsException() {
    CatalogEntity mappedEntity = buildMappedEntity("existing-entity");
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request = buildSaveIndividualRequest(
        "uuid-123", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER, "Custom Name", "custom_id");
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityRegisterOnly(request, entityMappedEntityResponse, "uuid-123",
                mappedEntity, null, TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject", "services", "service",
                "test-entity", "linkagePath", Collections.emptyMap(), config, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot register")
        .hasMessageContaining("already linked");
  }

  // ---- processEntityMergeOnly tests ----

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityMergeOnly_AllTrue_Unmapped_ThrowsException() {
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request =
        buildSaveAllRequest(SaveDiscoverEntitiesRequest.SelectionFilterEnum.MERGE_RECOMMENDED);
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityMergeOnly(request, entityMappedEntityResponse, "uuid-123", null,
                null, TEST_ACCOUNT_IDENTIFIER, "linkagePath", Collections.emptyMap(), config, null, "test-entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot merge")
        .hasMessageContaining("neither linked nor correlated");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityMergeOnly_AllFalse_ActionRegister_ThrowsException() {
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request =
        buildSaveIndividualRequest("uuid-123", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityMergeOnly(request, entityMappedEntityResponse, "uuid-123", null,
                null, TEST_ACCOUNT_IDENTIFIER, "linkagePath", Collections.emptyMap(), config, null, "test-entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Action REGISTER is not allowed")
        .hasMessageContaining("restricts action to Merge");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityMergeOnly_AllFalse_NoDestination_Unmapped_ThrowsException() {
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequest request =
        buildSaveIndividualRequest("uuid-123", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE);
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityMergeOnly(request, entityMappedEntityResponse, "uuid-123", null,
                null, TEST_ACCOUNT_IDENTIFIER, "linkagePath", Collections.emptyMap(), config, null, "test-entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot merge")
        .hasMessageContaining("neither linked nor correlated");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityMergeOnly_AllFalse_InvalidDestination_ThrowsException() {
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequestIntegrationEntities entity = new SaveDiscoverEntitiesRequestIntegrationEntities();
    entity.setIntegrationEntityId("uuid-123");
    entity.setAction(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE);
    entity.setActionDestination("invalid-destination");
    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest();
    request.setSelectionFilter(SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL);
    request.setIntegrationEntities(List.of(entity));
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(
        ()
            -> catalogIntegrationService.processEntityMergeOnly(request, entityMappedEntityResponse, "uuid-123", null,
                null, TEST_ACCOUNT_IDENTIFIER, "linkagePath", Collections.emptyMap(), config, null, "test-entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot merge")
        .hasMessageContaining("invalid destination");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessEntityMergeOnly_AllFalse_MappedEntityDifferentDestination_ThrowsException() {
    CatalogEntity mappedEntity = buildMappedEntity("existing-entity");
    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "services", "test-entity");
    SaveDiscoverEntitiesRequestIntegrationEntities entity = new SaveDiscoverEntitiesRequestIntegrationEntities();
    entity.setIntegrationEntityId("uuid-123");
    entity.setAction(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE);
    entity.setActionDestination("service:account.otherOrg/other-entity");
    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest();
    request.setSelectionFilter(SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL);
    request.setIntegrationEntities(List.of(entity));
    TypesIntegrationConfig config = buildIntegrationConfig();

    assertThatThrownBy(()
                           -> catalogIntegrationService.processEntityMergeOnly(request, entityMappedEntityResponse,
                               "uuid-123", mappedEntity, null, TEST_ACCOUNT_IDENTIFIER, "linkagePath",
                               Collections.emptyMap(), config, null, "test-entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot merge")
        .hasMessageContaining("already linked")
        .hasMessageContaining("cannot link to different entity");
  }

  // ---- matchesCorrelationFieldInEntity with operator tests ----

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPerformAdditionalLinkageOnCatalogEntity_SkipsNullMetadataTags() throws Exception {
    CatalogEntity catalogEntity = InlineCatalogEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .orgIdentifier("testOrg")
                                      .projectIdentifier("testProject")
                                      .identifier("test-entity")
                                      .kind(COMPONENT_KIND)
                                      .apiVersion("harness.io/v1")
                                      .yaml("apiVersion: harness.io/v1\nkind: component\nidentifier: test-entity")
                                      .build();

    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "component", "test-entity");
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("tags", null);
    metadata.put("annotations", Map.of("harness.io/services", "service-url"));
    entityMappedEntityResponse.getData().put("metadata", metadata);

    Method additionalLinkageMethod = CatalogIntegrationServiceImpl.class.getDeclaredMethod(
        "performAdditionalLinkageOnCatalogEntity", EntityMappedEntityResponse.class, CatalogEntity.class, Map.class);
    additionalLinkageMethod.setAccessible(true);
    additionalLinkageMethod.invoke(
        catalogIntegrationService, entityMappedEntityResponse, catalogEntity, Collections.emptyMap());

    ArgumentCaptor<EntityUpdateRequest> entityUpdateRequestCaptor = ArgumentCaptor.forClass(EntityUpdateRequest.class);
    verify(catalogService)
        .updateEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"),
            eq("component:account.testOrg.testProject/test-entity"), entityUpdateRequestCaptor.capture(), eq(false),
            eq(true), eq(false), eq(false));

    Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(entityUpdateRequestCaptor.getValue().getYaml());
    Map<String, Object> yamlMetadata = (Map<String, Object>) yamlMap.get("metadata");

    assertThat(yamlMetadata).doesNotContainKey("tags");
    assertThat((Map<String, Object>) yamlMetadata.get("annotations"))
        .containsEntry("harness.io/services", "service-url");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDecorateIDPCatalogWithIntegrationMetadata_RemovesNullProcessedMetadataTags() throws Exception {
    CatalogEntity catalogEntity = InlineCatalogEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .identifier("test-entity")
                                      .kind(COMPONENT_KIND)
                                      .apiVersion("harness.io/v1")
                                      .parentUniqueId("test-parent")
                                      .yaml("apiVersion: harness.io/v1\nkind: component\nidentifier: test-entity")
                                      .build();

    Map<String, Object> processedMetadata = new HashMap<>();
    processedMetadata.put("tags", null);
    Map<String, Object> processedData = new HashMap<>();
    processedData.put("metadata", processedMetadata);
    Map<String, Object> decorator = new HashMap<>();
    decorator.put("_processed_data", processedData);
    catalogEntity.setDecorator(decorator);

    EntityMappedEntityResponse entityMappedEntityResponse =
        buildEntityMappedEntityResponse("uuid-123", "component", "test-entity");
    entityMappedEntityResponse.getData().put("metadata", new HashMap<>());

    Method decorateMethod =
        CatalogIntegrationServiceImpl.class.getDeclaredMethod("decorateIDPCatalogWithIntegrationMetadata",
            CatalogEntity.class, String.class, EntityMappedEntityResponse.class, String.class,
            TypesIntegrationConfig.EnumIntegrationType.class, String.class, String.class);
    decorateMethod.setAccessible(true);
    decorateMethod.invoke(catalogIntegrationService, catalogEntity,
        "metadata.integration.account.harness-cd-integration.service", entityMappedEntityResponse, "MERGE",
        TypesIntegrationConfig.EnumIntegrationType.HarnessCD, "account", "harness-cd-integration");

    Map<String, Object> updatedProcessedData = catalogEntity.getFailSafeProcessedData();
    Map<String, Object> updatedMetadata = (Map<String, Object>) updatedProcessedData.get("metadata");
    assertThat(updatedMetadata).doesNotContainKey("tags");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_ContainsOperator_MatchesInDecorator() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    repo_url: \"my-service\"");
    entityResponse.setYaml("apiVersion: harness.io/v1\nkind: component\nmetadata:\n  name: test");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.repo_url", "https://github.com/org/my-service-repo", "contains");

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_ContainsOperator_NoMatch() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    repo_url: \"other-repo\"");
    entityResponse.setYaml("apiVersion: harness.io/v1\nkind: component\nmetadata:\n  name: test");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.repo_url", "my-service", "contains");

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_ContainsOperator_CaseInsensitive() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    repo_url: \"My-Service\"");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.repo_url", "https://github.com/org/my-service-repo", "contains");

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_NullOperator_UsesEquals() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    service_now_id: \"SYS123456\"");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "SYS123456", null);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMatchesCorrelationFieldInEntity_NullOperator_PartialDoesNotMatch() {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account/test-entity");
    entityResponse.setDecorator("metadata:\n  annotations:\n    service_now_id: \"SYS123456\"");

    boolean result = catalogIntegrationService.matchesCorrelationFieldInEntity(
        entityResponse, ".metadata.annotations.service_now_id", "SYS123", null);

    assertThat(result).isFalse();
  }

  // ---- discoverEntities Template→Workflow correlation ----

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToCatalogKindForDiscoverLookupRemapsTemplateToWorkflow() {
    assertThat(catalogIntegrationService.toCatalogKindForDiscoverLookup("template")).isEqualTo("workflow");
    assertThat(catalogIntegrationService.toCatalogKindForDiscoverLookup("Template")).isEqualTo("workflow");
    assertThat(catalogIntegrationService.toCatalogKindForDiscoverLookup("component")).isEqualTo("component");
    assertThat(catalogIntegrationService.toCatalogKindForDiscoverLookup(null)).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDiscoverEntitiesTemplateKindCorrelatesAgainstWorkflowAndDropsFromDiscovered() throws Exception {
    String integrationId = "catalog_info_integration";
    String templateUuid = "uuid-template-1";

    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.setUuid(templateUuid);
    entity.setName("my_template");
    entity.getData().put("kind", "template");
    entity.getData().put("type", "service");
    entity.getEntityInfo().setIdentifier("my_template");

    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    config.getConfiguration().put("all_scopes", true);

    stubDiscoverEntitiesImCalls(config, List.of(entity));

    EntityResponse workflowEntity = new EntityResponse();
    workflowEntity.setKindIdentifier("workflow");
    workflowEntity.setType("service");
    workflowEntity.setName("my_template");
    workflowEntity.setEntityRef("workflow:account/my_template");
    workflowEntity.setDecorator("_processed_data:\n"
        + "  metadata:\n"
        + "    integration:\n"
        + "      account:\n"
        + "        catalog_info_integration:\n"
        + "          catalog_info_file:\n"
        + "            entity_uuid: " + templateUuid + "\n");

    when(catalogService.getEntities(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(),
             any(), any(), any(), eq("workflow"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(GetEntitiesDTO.builder().entityResponses(List.of(workflowEntity)).build());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        integrationId, 0, 10, "created,desc", null, "catalog_info_file", null, null, null, null, null);

    assertThat(result.getDiscoverEntitiesResponses()).isEmpty();
    verify(catalogService, never())
        .getEntitiesV2(any(), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    verify(catalogService)
        .getEntities(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(),
            any(), eq("workflow"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    verify(catalogService, never())
        .getEntities(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(),
            any(), eq("template"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDiscoverEntitiesUnimportedTemplateKeepsTemplateKindInResponse() throws Exception {
    String integrationId = "catalog_info_integration";
    String templateUuid = "uuid-template-unimported";

    EntityMappedEntityResponse entity = buildCatalogInfoResponse();
    entity.setUuid(templateUuid);
    entity.setName("my_template");
    entity.getData().put("kind", "template");
    entity.getData().put("type", "service");
    entity.getEntityInfo().setIdentifier("my_template");

    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    config.getConfiguration().put("all_scopes", true);

    stubDiscoverEntitiesImCalls(config, List.of(entity));

    when(catalogService.getEntities(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(),
             any(), any(), any(), eq("workflow"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(GetEntitiesDTO.builder().entityResponses(Collections.emptyList()).build());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        integrationId, 0, 10, "created,desc", null, "catalog_info_file", null, null, null, null, null);

    assertThat(result.getDiscoverEntitiesResponses()).hasSize(1);
    DiscoverEntitiesResponse discoverResponse = result.getDiscoverEntitiesResponses().get(0);
    assertThat(discoverResponse.getIntegrationEntityId()).isEqualTo(templateUuid);
    assertThat(discoverResponse.getKind()).isEqualTo("template");
    assertThat(discoverResponse.getName()).isEqualTo("my_template");
    verify(catalogService, never())
        .getEntitiesV2(any(), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDiscoverEntitiesMixedTemplateAndComponentUsesNarrowMainAndSpecialQueries() throws Exception {
    String integrationId = "catalog_info_integration";

    EntityMappedEntityResponse templateEntity = buildCatalogInfoResponse();
    templateEntity.setUuid("uuid-template-mixed");
    templateEntity.setName("my_template");
    templateEntity.getData().put("kind", "template");
    templateEntity.getData().put("type", "service");
    templateEntity.getEntityInfo().setIdentifier("my_template");

    EntityMappedEntityResponse componentEntity = buildCatalogInfoResponse();
    componentEntity.setUuid("uuid-component-mixed");
    componentEntity.setName("my_component");
    componentEntity.getData().put("kind", "component");
    componentEntity.getData().put("type", "service");
    componentEntity.getEntityInfo().setIdentifier("my_component");

    TypesIntegrationConfig config = buildCatalogInfoConfig(false);
    config.getConfiguration().put("all_scopes", true);

    stubDiscoverEntitiesImCalls(config, List.of(templateEntity, componentEntity));

    when(
        catalogService.getEntitiesV2(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(),
            any(), any(), any(), eq("component"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(GetEntitiesDTO.builder().entityResponses(Collections.emptyList()).build());
    when(catalogService.getEntities(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(),
             any(), any(), any(), eq("workflow"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(GetEntitiesDTO.builder().entityResponses(Collections.emptyList()).build());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        integrationId, 0, 10, "created,desc", null, "catalog_info_file", null, null, null, null, null);

    assertThat(result.getDiscoverEntitiesResponses()).hasSize(2);
    verify(catalogService)
        .getEntitiesV2(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(),
            any(), eq("component"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    verify(catalogService)
        .getEntities(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(),
            any(), eq("workflow"), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    verify(catalogService, never())
        .getEntitiesV2(eq(TEST_ACCOUNT_IDENTIFIER), anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any(), any(),
            any(), eq(""), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
  }

  private CatalogEntity stubBulkUnlink(TypesIntegrationConfig config) throws Exception {
    stubIntegrationConfig(config);
    String entityRef = "component:account.testOrg.testProject/service-identifier";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                              .orgIdentifier("testOrg")
                              .projectIdentifier("testProject")
                              .uniqueId("scope-unique-id")
                              .build();
    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(TEST_ACCOUNT_IDENTIFIER, null, entityRef))
        .thenReturn(Pair.of(List.of(scopeInfo), Collections.emptyMap()));

    Map<String, Object> linkage = new HashMap<>();
    linkage.put("entity_uuid", "entity-uuid");
    linkage.put("entity_action", "REGISTER");
    Map<String, Object> integrationKinds = new HashMap<>();
    integrationKinds.put("component", linkage);
    Map<String, Object> integrationIds = new HashMap<>();
    integrationIds.put(HARNESS_CI_INTEGRATION_ID, integrationKinds);
    Map<String, Object> spaces = new HashMap<>();
    spaces.put("account", integrationIds);
    Map<String, Object> metadataIntegration = new HashMap<>();
    metadataIntegration.put("integration", spaces);
    Map<String, Object> processedData = new HashMap<>();
    processedData.put("metadata", metadataIntegration);
    Map<String, Object> decorator = new HashMap<>();
    decorator.put(PROCESSED_DATA, processedData);

    CatalogEntity catalogEntity = InlineCatalogEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .orgIdentifier("testOrg")
                                      .projectIdentifier("testProject")
                                      .identifier("service-identifier")
                                      .name("Service")
                                      .kind(COMPONENT_KIND)
                                      .uniqueId("catalog-unique-id")
                                      .parentUniqueId("scope-unique-id")
                                      .yaml("apiVersion: harness.io/v1\n"
                                          + "kind: Component\n"
                                          + "type: service\n"
                                          + "identifier: service-identifier\n"
                                          + "name: Service\n")
                                      .decorator(decorator)
                                      .build();
    when(catalogEntityRepository.getEntitiesForEntityRefsAndKinds(
             TEST_ACCOUNT_IDENTIFIER, entityRef, List.of(scopeInfo), List.of()))
        .thenReturn(List.of(catalogEntity));
    when(catalogService.getEntity(TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject", entityRef, false, false, true))
        .thenReturn(new EntityResponse().entityRef(entityRef).identifier("service-identifier"));
    when(integrationManagerClientHelper.getIntegrationManagerIdpMappingId()).thenReturn("idp-mapping-id");
    return catalogEntity;
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUnlinkIntegrationEntitiesHarnessCISkipsIntegrationManagerUnsubscribe() throws Exception {
    CatalogEntity catalogEntity = stubBulkUnlink(buildHarnessCIConfig());
    String entityRef = "component:account.testOrg.testProject/service-identifier";

    UnlinkIntegrationEntitiesResponse response = catalogIntegrationService.unlinkIntegrationEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, HARNESS_CI_INTEGRATION_ID, List.of(entityRef));

    assertThat(response.getSuccess()).extracting("entityRef").containsExactly(entityRef);
    assertThat(response.getFailed()).isEmpty();
    verify(integrationManagerClientHelper, never())
        .unsubscribeFromEntityUpdates(any(), any(), any(), any(), any(), any());
    verify(catalogEntityRepository).save(catalogEntity);
    verify(catalogService)
        .updateEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(entityRef),
            any(EntityUpdateRequest.class), eq(false), eq(false), eq(false), eq(false));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUnlinkIntegrationEntitiesNonCIStillUnsubscribesFromIntegrationManager() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    stubBulkUnlink(config);
    String entityRef = "component:account.testOrg.testProject/service-identifier";
    when(integrationManagerClientHelper.unsubscribeFromEntityUpdates(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any()))
        .thenReturn(subscribeCall);
    EntitySubscribeEntitiesResponse unsubscribeResponse = new EntitySubscribeEntitiesResponse();
    unsubscribeResponse.setSuccess(List.of(new EntitySubscribeEntitiesResponse.EntitySubscriptionSuccess()));
    when(subscribeCall.execute()).thenReturn(Response.success(unsubscribeResponse));

    UnlinkIntegrationEntitiesResponse response = catalogIntegrationService.unlinkIntegrationEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, HARNESS_CI_INTEGRATION_ID, List.of(entityRef));

    assertThat(response.getSuccess()).extracting("entityRef").containsExactly(entityRef);
    assertThat(response.getFailed()).isEmpty();
    verify(integrationManagerClientHelper)
        .unsubscribeFromEntityUpdates(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(),
            eq(HARNESS_CI_INTEGRATION_ID), any());
  }

  private EntityMappedEntityResponse githubMappedEntityWithOrg() {
    EntityMappedEntityResponse entity = buildEntityMappedEntityResponse("github-1", "component", "github-service");
    entity.setKind("repo_details");
    entity.getData().put("org", "wrong-top-level-value");
    entity.getData().put(
        "metadata", Map.of("integration_properties", Map.of("GitHub", Map.of("org", "acme", "isPrivate", false))));
    return entity;
  }

  private void stubDiscoverEntitiesImCalls(TypesIntegrationConfig config, List<EntityMappedEntityResponse> entities)
      throws Exception {
    EntityMappedEntityResponseObject responseObject = new EntityMappedEntityResponseObject(entities);
    okhttp3.Headers headers = new okhttp3.Headers.Builder()
                                  .add("x-total", String.valueOf(entities.size()))
                                  .add("x-total-pages", "1")
                                  .add("x-page", "0")
                                  .add("x-per-page", "10")
                                  .build();
    when(integrationManagerClientHelper.getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(),
             any(), anyInt(), anyInt(), any(), any(), anyBoolean()))
        .thenReturn(mappedEntitiesCall);
    when(mappedEntitiesCall.execute()).thenReturn(Response.success(responseObject, headers));
    when(integrationManagerClientHelper.getIntegrationManagerIdpMappingId()).thenReturn("idp-mapping-id");

    when(integrationManagerClientHelper.getIntegrationConfig(any(), any(), any(), any(), any()))
        .thenReturn(integrationConfigCall);
    when(integrationConfigCall.execute()).thenReturn(Response.success(config));
  }

  // ---- discoverEntities HarnessCI offset pagination tests ----

  private static final String HARNESS_CI_INTEGRATION_ID = "harness_ci";

  private TypesIntegrationConfig buildHarnessCIConfig() {
    TypesIntegrationConfig config = new TypesIntegrationConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.HarnessCI);
    config.setIntegrationMode(TypesIntegrationConfig.IntegrationMode.platform);
    config.setSpacePath(TEST_ACCOUNT_IDENTIFIER);
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("all_scopes", true);
    config.setConfiguration(configuration);
    return config;
  }

  private TypesIntegrationConfig buildNonCIConfig() {
    TypesIntegrationConfig config = new TypesIntegrationConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.HarnessCD);
    config.setIntegrationMode(TypesIntegrationConfig.IntegrationMode.platform);
    config.setSpacePath(TEST_ACCOUNT_IDENTIFIER);
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("all_scopes", true);
    config.setConfiguration(configuration);
    return config;
  }

  private void putFieldMapping(TypesIntegrationConfig config, String kind, String sourceField, String targetField) {
    Map<String, Object> configuration = new HashMap<>(config.getConfiguration());
    configuration.put("field_mappings_per_kind",
        Map.of(kind, List.of(Map.of("source_field", sourceField, "target_field", targetField))));
    config.setConfiguration(configuration);
  }

  private void stubIntegrationConfig(TypesIntegrationConfig config) throws Exception {
    when(integrationManagerClientHelper.getIntegrationConfig(any(), any(), any(), any(), any()))
        .thenReturn(integrationConfigCall);
    when(integrationConfigCall.execute()).thenReturn(Response.success(config));
    when(integrationManagerClientHelper.getIntegrationManagerIdpMappingId()).thenReturn("idp-mapping-id");
  }

  private void stubEmptyCatalogEntities() {
    when(catalogService.getEntitiesV2(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(GetEntitiesDTO.builder().entityResponses(Collections.emptyList()).totalElements(0).build());
  }

  private Response<EntityMappedEntityResponseObject> mappedEntitiesOffsetResponse(
      List<EntityMappedEntityResponse> entities, Integer totalElements) {
    okhttp3.Headers.Builder headers = new okhttp3.Headers.Builder();
    if (totalElements != null) {
      headers.add("x-total", String.valueOf(totalElements));
    }
    return Response.success(new EntityMappedEntityResponseObject(entities), headers.build());
  }

  private void stubMappedEntitiesByOffset(List<EntityMappedEntityResponse> source, int totalElements) throws Exception {
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(), any(),
             anyInt(), anyInt(), any(), any(), eq(false)))
        .thenAnswer(invocation -> {
          int offset = invocation.getArgument(9);
          int limit = invocation.getArgument(10);
          int toIndex = Math.min(offset + limit, source.size());
          List<EntityMappedEntityResponse> window =
              offset >= source.size() ? List.of() : source.subList(offset, toIndex);
          Call<EntityMappedEntityResponseObject> call = mock(Call.class);
          when(call.execute()).thenReturn(mappedEntitiesOffsetResponse(window, totalElements));
          return call;
        });
  }

  private List<EntityMappedEntityResponse> buildDenseOffsetSource(int totalSize, Set<Integer> eligibleOffsets) {
    List<EntityMappedEntityResponse> source = new ArrayList<>(totalSize);
    for (int i = 0; i < totalSize; i++) {
      String uuid = eligibleOffsets.contains(i) ? "elig-" + i : "imp-" + i;
      source.add(buildEntityMappedEntityResponse(uuid, "component", uuid));
    }
    return source;
  }

  private void stubImportedCatalogEntities(String... importedUuids) {
    List<EntityResponse> imported = new ArrayList<>();
    for (String uuid : importedUuids) {
      imported.add(buildImportedCatalogEntity(uuid));
    }
    when(catalogService.getEntitiesV2(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(GetEntitiesDTO.builder().entityResponses(imported).totalElements(imported.size()).build());
  }

  private void stubImportedCatalogEntitiesForDenseSource(int totalSize, Set<Integer> eligibleOffsets) {
    List<String> importedUuids = new ArrayList<>();
    for (int i = 0; i < totalSize; i++) {
      if (!eligibleOffsets.contains(i)) {
        importedUuids.add("imp-" + i);
      }
    }
    stubImportedCatalogEntities(importedUuids.toArray(new String[0]));
  }

  private EntityResponse buildImportedCatalogEntity(String importedUuid) {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setEntityRef("component:account.testOrg.testProject/imported-" + importedUuid);
    entityResponse.setKindIdentifier("component");
    entityResponse.setType("service");
    entityResponse.setName("imported-" + importedUuid);
    entityResponse.setDecorator("metadata:\n  integration:\n    account:\n      " + HARNESS_CI_INTEGRATION_ID
        + ":\n        component:\n          entity_uuid: " + importedUuid + "\n");
    return entityResponse;
  }

  @SuppressWarnings("unchecked")
  private List<EntityMappedEntityResponse> invokeGetHarnessCIIntegrationEntities(
      boolean fetchAll, List<String> requestedUuids) throws Exception {
    Method method = CatalogIntegrationServiceImpl.class.getDeclaredMethod("getHarnessCIIntegrationEntities",
        String.class, String.class, String.class, String.class, boolean.class, boolean.class, List.class);
    method.setAccessible(true);
    return (List<EntityMappedEntityResponse>) method.invoke(catalogIntegrationService, TEST_ACCOUNT_IDENTIFIER, null,
        null, HARNESS_CI_INTEGRATION_ID, fetchAll, true, requestedUuids);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIManualImportFetchDoesNotRequirePageHeaders() throws Exception {
    EntityMappedEntityResponse unrelated =
        buildEntityMappedEntityResponse("unrelated-uuid", "component", "unrelated-service");
    EntityMappedEntityResponse entity = buildEntityMappedEntityResponse("entity-uuid", "component", "service");
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true),
             eq("name"), eq("asc"), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute())
        .thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of(unrelated, entity))));
    when(integrationManagerClientHelper.getIntegrationManagerIdpMappingId()).thenReturn("idp-mapping-id");

    List<EntityMappedEntityResponse> result = invokeGetHarnessCIIntegrationEntities(false, List.of("entity-uuid"));

    assertThat(result).extracting(EntityMappedEntityResponse::getUuid).containsExactly("entity-uuid");
    verify(integrationManagerClientHelper, never())
        .getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt(),
            any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIManualImportReturnsMultipleRequestedEntitiesOnlyOnce() throws Exception {
    EntityMappedEntityResponse first = buildEntityMappedEntityResponse("first-uuid", "component", "first-service");
    EntityMappedEntityResponse duplicate =
        buildEntityMappedEntityResponse("first-uuid", "component", "duplicate-service");
    EntityMappedEntityResponse second = buildEntityMappedEntityResponse("second-uuid", "component", "second-service");
    EntityMappedEntityResponse unrelated =
        buildEntityMappedEntityResponse("unrelated-uuid", "component", "unrelated-service");
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true),
             eq("name"), eq("asc"), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute())
        .thenReturn(
            Response.success(new EntityMappedEntityResponseObject(List.of(first, unrelated, duplicate, second))));

    List<EntityMappedEntityResponse> result =
        invokeGetHarnessCIIntegrationEntities(false, List.of("first-uuid", "second-uuid"));

    assertThat(result).extracting(EntityMappedEntityResponse::getUuid).containsExactly("first-uuid", "second-uuid");
    verify(offsetCall, times(1)).execute();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIImportAllFetchesUntilUnderfilledWindow() throws Exception {
    List<EntityMappedEntityResponse> fullWindow = new ArrayList<>();
    for (int i = 0; i < 2000; i++) {
      fullWindow.add(buildEntityMappedEntityResponse("uuid-" + i, "component", "service-" + i));
    }
    EntityMappedEntityResponse finalEntity = buildEntityMappedEntityResponse("uuid-2000", "component", "service-2000");
    Call<EntityMappedEntityResponseObject> firstCall = mock(Call.class);
    Call<EntityMappedEntityResponseObject> secondCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true),
             eq("name"), eq("asc"), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(firstCall);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true),
             eq("name"), eq("asc"), eq(2000), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(secondCall);
    when(firstCall.execute()).thenReturn(Response.success(new EntityMappedEntityResponseObject(fullWindow)));
    when(secondCall.execute()).thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of(finalEntity))));

    List<EntityMappedEntityResponse> result = invokeGetHarnessCIIntegrationEntities(true, List.of());

    assertThat(result).hasSize(2001);
    assertThat(result.get(0).getUuid()).isEqualTo("uuid-0");
    assertThat(result.get(2000).getUuid()).isEqualTo("uuid-2000");
    verify(firstCall, times(1)).execute();
    verify(secondCall, times(1)).execute();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIImportAllUpdatesAutoDiscoveryAndUsesOffsetFetch() throws Exception {
    TypesIntegrationConfig config = buildHarnessCIConfig();
    stubIntegrationConfig(config);
    when(integrationManagerClientHelper.updateIntegrationConfig(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any()))
        .thenReturn(integrationConfigCall);
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true),
             eq("name"), eq("asc"), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute()).thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of())));
    SaveDiscoverEntitiesRequest request = buildSaveAllRequest(SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL);
    request.setAutoDiscover(true);
    UserPrincipal userPrincipal =
        UserPrincipal.newBuilder().setUuid("user-uuid").setName("Test User").setEmail("test@harness.io").build();

    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, null, null, HARNESS_CI_INTEGRATION_ID, request, userPrincipal);

    assertThat(config.getConfiguration()).containsEntry("auto_import", true);
    verify(integrationManagerClientHelper, times(1))
        .updateIntegrationConfig(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(),
            eq(HARNESS_CI_INTEGRATION_ID), any());
    verify(integrationManagerClientHelper, never())
        .getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt(),
            any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIImportPropagatesIntegrationManagerFailure() throws Exception {
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true),
             eq("name"), eq("asc"), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute()).thenThrow(new IllegalStateException("IM unavailable"));

    assertThatThrownBy(() -> invokeGetHarnessCIIntegrationEntities(false, List.of("entity-uuid")))
        .hasCauseInstanceOf(UnexpectedException.class)
        .hasRootCauseMessage("IM unavailable");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIManualImportFailsWhenRequestedEntityIsMissing() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(),
             any(), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute()).thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of())));

    SaveDiscoverEntitiesRequest request = buildSaveIndividualRequest(
        "missing-uuid", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER, null, null);

    assertThatThrownBy(()
                           -> catalogIntegrationService.saveDiscoverEntitiesInternal(
                               TEST_ACCOUNT_IDENTIFIER, null, null, HARNESS_CI_INTEGRATION_ID, request, null))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("missing-uuid");
    verify(integrationManagerClientHelper, never())
        .getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt(),
            any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIManualImportRegistersReturnedEntity() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    EntityMappedEntityResponse entity =
        buildEntityMappedEntityResponse("entity-uuid", "component", "service-identifier");
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(),
             any(), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute()).thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of(entity))));
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                              .orgIdentifier("testOrg")
                              .projectIdentifier("testProject")
                              .uniqueId("scope-unique-id")
                              .build();
    when(catalogServiceHelper.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject")).thenReturn(scopeInfo);
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any())).thenReturn(Collections.emptyList());
    KindEntity componentKind = new KindEntity();
    componentKind.setIdentifier(COMPONENT_KIND);
    componentKind.setKindType(KindType.BUILT_IN);
    when(kindEntityRepository.findAllByAccountIdentifierInAndIdentifierIn(any(), eq(List.of(COMPONENT_KIND))))
        .thenReturn(List.of(componentKind));
    EntityResponse createdResponse = new EntityResponse();
    createdResponse.setIdentifier("Test_Entity");
    when(catalogService.createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false),
             eq(false), any(EntityCreateRequest.class)))
        .thenReturn(createdResponse);
    CatalogEntity createdEntity = InlineCatalogEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                      .orgIdentifier("testOrg")
                                      .projectIdentifier("testProject")
                                      .identifier("Test_Entity")
                                      .kind(COMPONENT_KIND)
                                      .uniqueId("created-unique-id")
                                      .parentUniqueId("scope-unique-id")
                                      .build();
    when(catalogServiceHelper.catalogEntity(
             TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject", "component", "Test_Entity"))
        .thenReturn(createdEntity);
    when(catalogEntityRepository.findById("created-unique-id")).thenReturn(Optional.empty());
    SaveDiscoverEntitiesRequest request = buildSaveIndividualRequest(
        "entity-uuid", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER, null, null);

    catalogIntegrationService.saveDiscoverEntitiesInternal(
        TEST_ACCOUNT_IDENTIFIER, null, null, HARNESS_CI_INTEGRATION_ID, request, null);

    verify(catalogService, times(1))
        .createEntity(eq(TEST_ACCOUNT_IDENTIFIER), eq("testOrg"), eq("testProject"), eq(false), eq(false), any());
    verify(catalogEntityRepository, times(1)).save(createdEntity);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testHarnessCIManualImportPropagatesEntityProcessingFailure() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    EntityMappedEntityResponse entity =
        buildEntityMappedEntityResponse("entity-uuid", "component", "service-identifier");
    Call<EntityMappedEntityResponseObject> offsetCall = mock(Call.class);
    when(integrationManagerClientHelper.getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER),
             eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(), eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(),
             any(), eq(0), eq(2000), isNull(), any(), eq(false)))
        .thenReturn(offsetCall);
    when(offsetCall.execute()).thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of(entity))));
    when(catalogServiceHelper.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, "testOrg", "testProject"))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .orgIdentifier("testOrg")
                        .projectIdentifier("testProject")
                        .uniqueId("scope-unique-id")
                        .build());
    when(catalogEntityRepository.getEntitiesFilters(any(), any(), any()))
        .thenThrow(new IllegalStateException("catalog lookup failed"));
    SaveDiscoverEntitiesRequest request = buildSaveIndividualRequest(
        "entity-uuid", SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER, null, null);

    assertThatThrownBy(()
                           -> catalogIntegrationService.saveDiscoverEntitiesInternal(
                               TEST_ACCOUNT_IDENTIFIER, null, null, HARNESS_CI_INTEGRATION_ID, request, null))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Error while registering HarnessCI integration entity entity-uuid")
        .hasRootCauseMessage("catalog lookup failed");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_UsesOffsetPathAndIgnoresPageIndex() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    stubEmptyCatalogEntities();
    List<EntityMappedEntityResponse> source = List.of(buildEntityMappedEntityResponse("e0", "component", "svc0"),
        buildEntityMappedEntityResponse("e1", "component", "svc1"));
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 99, 2, null, null, null, null, null, null, null, null);

    assertThat(result.isOffsetPagination()).isTrue();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.getDiscoverEntitiesResponses()).hasSize(2);
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isNull();
    verify(integrationManagerClientHelper, never())
        .getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt(),
            any(), any(), anyBoolean());
    verify(integrationManagerClientHelper)
        .getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(),
            eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(), any(), eq(0), eq(100), any(), any(), eq(false));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_ReturnsMergeSuggestionsPerEntity() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    EntityMappedEntityResponse sourceEntity =
        buildEntityMappedEntityResponse("ci-entity", "component", "discovered-service");
    stubMappedEntitiesByOffset(List.of(sourceEntity), 1);

    EntityResponse sameScopeCandidate = new EntityResponse();
    sameScopeCandidate.setEntityRef("component:account.testOrg.testProject/existing-service");
    sameScopeCandidate.setKindIdentifier("component");
    sameScopeCandidate.setType("service");
    sameScopeCandidate.setName("Existing Service");
    EntityResponse otherScopeCandidate = new EntityResponse();
    otherScopeCandidate.setEntityRef("component:account.otherOrg.otherProject/other-service");
    otherScopeCandidate.setKindIdentifier("component");
    otherScopeCandidate.setType("service");
    otherScopeCandidate.setName("Other Service");
    when(catalogService.getEntitiesV2(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn(GetEntitiesDTO.builder()
                        .entityResponses(List.of(sameScopeCandidate, otherScopeCandidate))
                        .totalElements(2)
                        .build());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 10, null, null, null, null, null, null, null, null);

    assertThat(result.getMergeSuggestions()).isEmpty();
    assertThat(result.getDiscoverEntitiesResponses()).hasSize(1);
    assertThat(result.getDiscoverEntitiesResponses().get(0).getActionDestination().getMergeSuggestions())
        .extracting(DiscoverEntitiesResponseActionDestinationMerge::getEntityRef)
        .containsExactly("component:account.testOrg.testProject/existing-service");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_UnknownTotalDoesNotSuppressNextOffset() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    stubEmptyCatalogEntities();
    List<EntityMappedEntityResponse> source = List.of(buildEntityMappedEntityResponse("e0", "component", "svc0"),
        buildEntityMappedEntityResponse("e1", "component", "svc1"));
    stubMappedEntitiesByOffset(source, -1);

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 1, null, null, null, null, null, null, null, null);

    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("e0");
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_NonCI_UsesPagePathWithUnsubscribedOnly() throws Exception {
    stubIntegrationConfig(buildNonCIConfig());
    stubEmptyCatalogEntities();
    EntityMappedEntityResponse entity = buildEntityMappedEntityResponse("cd-1", "component", "cd-svc");
    okhttp3.Headers headers = new okhttp3.Headers.Builder()
                                  .add("x-total", "7")
                                  .add("x-total-pages", "1")
                                  .add("x-page", "0")
                                  .add("x-per-page", "10")
                                  .build();
    when(integrationManagerClientHelper.getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER),
             any(), any(), eq(TEST_IDENTIFIER), any(), eq(true), any(), any(), eq(2), eq(10), any(), any(), eq(true)))
        .thenReturn(mappedEntitiesCall);
    when(mappedEntitiesCall.execute())
        .thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of(entity)), headers));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 2, 10, null, null, null, null, null, null, null, null);

    assertThat(result.isOffsetPagination()).isFalse();
    assertThat(result.getTotalElements()).isEqualTo(7);
    assertThat(result.getDiscoverEntitiesResponses()).hasSize(1);
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isNull();
    verify(integrationManagerClientHelper, never())
        .getMappedEntitiesByOffset(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(),
            anyInt(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_AppliesFiltersPerKindWithoutPopulatingFields() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details", "team_repositories"));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(githubMappedEntityWithOrg()));

    DiscoverEntitiesDTO result =
        catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null,
            null, null, List.of("org:acme", "org:service", "isPrivate:false"), null, null, null, null);

    ArgumentCaptor<OpenapiGetMappedEntitiesRequest> requestCaptor =
        ArgumentCaptor.forClass(OpenapiGetMappedEntitiesRequest.class);
    verify(integrationManagerClientHelper)
        .getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(TEST_IDENTIFIER),
            any(), eq(true), any(), any(), eq(0), eq(10), any(), requestCaptor.capture(), eq(true));
    OpenapiGetMappedEntitiesRequest request = requestCaptor.getValue();
    assertThat(request.getKinds()).containsExactly("repo_details", "team_repositories");
    assertThat(request.getFieldValsPerKind()).containsOnlyKeys("repo_details", "team_repositories");
    request.getFieldValsPerKind().values().forEach(filters -> {
      assertThat(filters)
          .extracting(OpenapiGetMappedEntitiesRequest.FieldValFilter::getFieldName)
          .containsExactly("org", "isPrivate");
      assertThat(filters.get(0).getFieldValues()).containsExactly("acme", "service");
      assertThat(filters.get(1).getFieldValues()).containsExactly("false");
    });
    assertThat(result.getDiscoverEntitiesResponses()).hasSize(1);
    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsWithoutMappingAreNull() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details"));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(githubMappedEntityWithOrg()));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        TEST_IDENTIFIER, 0, 10, null, null, null, null, "org,isPrivate", null, null, null);

    verify(integrationManagerClientHelper)
        .getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(TEST_IDENTIFIER),
            any(), eq(true), any(), any(), eq(0), eq(10), any(), any(), eq(true));
    assertThat(result.getDiscoverEntitiesResponses()).hasSize(1);
    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields())
        .containsEntry("org", null)
        .containsEntry("isPrivate", null)
        .doesNotContainValue("wrong-top-level-value");
    assertThat(result.getDiscoverEntitiesResponses().get(0).getPaths()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsTrimsAndDropsBlankSegments() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details"));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(githubMappedEntityWithOrg()));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        TEST_IDENTIFIER, 0, 10, null, null, null, null, " org, isPrivate, ", null, null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields())
        .containsOnlyKeys("org", "isPrivate")
        .doesNotContainKey(" isPrivate")
        .doesNotContainKey("");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_FiltersAndIncludeFieldsAreIndependent() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details", "team_repositories"));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(githubMappedEntityWithOrg()));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        TEST_IDENTIFIER, 0, 10, null, null, null, List.of("org:acme"), "org", null, null, null);

    ArgumentCaptor<OpenapiGetMappedEntitiesRequest> requestCaptor =
        ArgumentCaptor.forClass(OpenapiGetMappedEntitiesRequest.class);
    verify(integrationManagerClientHelper)
        .getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(TEST_IDENTIFIER),
            any(), eq(true), any(), any(), eq(0), eq(10), any(), requestCaptor.capture(), eq(true));
    assertThat(requestCaptor.getValue().getFieldValsPerKind()).containsOnlyKeys("repo_details", "team_repositories");
    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields())
        .containsEntry("org", null)
        .doesNotContainKey("isPrivate");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsUsesFieldMappingTargetPath() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details"));
    putFieldMapping(config, "repo_details", "org", "metadata.custom.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.getData().put("metadata",
        Map.of("custom", Map.of("org", "from-mapping"), "integration_properties",
            Map.of("GitHub", Map.of("org", "acme", "isPrivate", false))));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        TEST_IDENTIFIER, 0, 10, null, null, null, null, "org,isPrivate", null, null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields())
        .containsEntry("org", "from-mapping")
        .containsEntry("isPrivate", null);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsRequiresExactSourceField() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details"));
    putFieldMapping(config, "repo_details", "spec.org", "metadata.custom.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.getData().put("metadata",
        Map.of("custom", Map.of("org", "from-spec-mapping"), "integration_properties",
            Map.of("GitHub", Map.of("org", "acme", "isPrivate", false))));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null, null, null, null, "org", null, null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields())
        .containsEntry("org", null)
        .doesNotContainValue("from-spec-mapping");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsNullWhenMappingPathIsMissing() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("aiasset_skill"));
    putFieldMapping(config, "aiasset_skill", "org", "spec.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.setKind("aiasset_skill");
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null, null, null, null, "org", null, null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).containsEntry("org", null);
    assertThat(result.getDiscoverEntitiesResponses().get(0).getPaths()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsUsesExplicitMappingToIntegrationProperty() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("aiasset_skill"));
    putFieldMapping(config, "aiasset_skill", "org", "metadata.integration_properties.GitHub.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.setKind("aiasset_skill");
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null, null, null, null, "org", null, null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).containsEntry("org", "acme");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludePathsReadDottedPathFromMappedData() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("aiasset_skill"));
    putFieldMapping(config, "aiasset_skill", "org", "spec.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.setKind("aiasset_skill");
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        TEST_IDENTIFIER, 0, 10, null, null, null, null, null, "metadata.integration_properties.GitHub.org", null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).isNullOrEmpty();
    assertThat(result.getDiscoverEntitiesResponses().get(0).getPaths())
        .containsEntry("metadata.integration_properties.GitHub.org", "acme")
        .doesNotContainKey("org")
        .doesNotContainValue("wrong-top-level-value");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludePathsReadTopLevelPathWithoutFieldMappings() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("aiasset_skill"));
    putFieldMapping(config, "aiasset_skill", "org", "metadata.integration_properties.GitHub.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.setKind("aiasset_skill");
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null, null, null, null, null, "org", null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).isNullOrEmpty();
    assertThat(result.getDiscoverEntitiesResponses().get(0).getPaths()).containsEntry("org", "wrong-top-level-value");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsAndIncludePathsAreIndependent() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("repo_details"));
    putFieldMapping(config, "repo_details", "org", "metadata.custom.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.getData().put("metadata",
        Map.of("custom", Map.of("org", "from-mapping"), "integration_properties",
            Map.of("GitHub", Map.of("org", "acme", "isPrivate", false))));
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result =
        catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null,
            null, null, null, "org", "metadata.integration_properties.GitHub.org", null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).containsEntry("org", "from-mapping");
    assertThat(result.getDiscoverEntitiesResponses().get(0).getPaths())
        .containsEntry("metadata.integration_properties.GitHub.org", "acme");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_IncludeFieldsAndIncludePathsSameTokenStaySeparate() throws Exception {
    TypesIntegrationConfig config = buildNonCIConfig();
    config.setIntegrationType(TypesIntegrationConfig.EnumIntegrationType.GitHub);
    config.setKinds(List.of("aiasset_skill"));
    putFieldMapping(config, "aiasset_skill", "organization", "metadata.custom.org");
    EntityMappedEntityResponse entity = githubMappedEntityWithOrg();
    entity.setKind("aiasset_skill");
    stubEmptyCatalogEntities();
    stubDiscoverEntitiesImCalls(config, List.of(entity));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 0, 10, null, null, null, null, "org", "org", null, null);

    assertThat(result.getDiscoverEntitiesResponses().get(0).getFields()).containsEntry("org", null);
    assertThat(result.getDiscoverEntitiesResponses().get(0).getPaths()).containsEntry("org", "wrong-top-level-value");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_InvalidFilterThrows() throws Exception {
    stubIntegrationConfig(buildNonCIConfig());

    assertThatThrownBy(()
                           -> catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
                               TEST_IDENTIFIER, 0, 10, null, null, null, List.of("org"), null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("field_name:value");
    verify(integrationManagerClientHelper, never())
        .getMappedEntities(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt(),
            any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_NonCI_EmptyPagePreservesLegacyPagination() throws Exception {
    stubIntegrationConfig(buildNonCIConfig());
    okhttp3.Headers headers = new okhttp3.Headers.Builder()
                                  .add("x-total", "0")
                                  .add("x-total-pages", "0")
                                  .add("x-page", "3")
                                  .add("x-per-page", "10")
                                  .build();
    when(integrationManagerClientHelper.getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER),
             any(), any(), eq(TEST_IDENTIFIER), any(), eq(true), any(), any(), eq(3), eq(10), any(), any(), eq(true)))
        .thenReturn(mappedEntitiesCall);
    when(mappedEntitiesCall.execute())
        .thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of()), headers));

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 3, 10, null, null, null, null, null, null, 5, 15);

    assertThat(result.isOffsetPagination()).isFalse();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.getDiscoverEntitiesResponses()).isEmpty();
    assertThat(result.getMergeSuggestions()).isEmpty();
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isNull();
    verify(integrationManagerClientHelper, never())
        .getMappedEntitiesByOffset(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(),
            anyInt(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_BothOffsetsThrow() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());

    assertThatThrownBy(()
                           -> catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
                               HARNESS_CI_INTEGRATION_ID, 0, 10, null, null, null, null, null, null, 1, 2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("prevOffset and nextOffset cannot both be provided");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_NegativeOffsetsThrow() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());

    assertThatThrownBy(()
                           -> catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
                               HARNESS_CI_INTEGRATION_ID, 0, 10, null, null, null, null, null, null, -1, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Pagination offsets cannot be negative");

    assertThatThrownBy(()
                           -> catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
                               HARNESS_CI_INTEGRATION_ID, 0, 10, null, null, null, null, null, null, null, -5))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Pagination offsets cannot be negative");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_ForwardRefillsAcrossWindowsExcludingImported() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    int totalSize = 103;
    Set<Integer> eligibleOffsets = Set.of(1, 101, 102);
    List<EntityMappedEntityResponse> source = buildDenseOffsetSource(totalSize, eligibleOffsets);
    stubImportedCatalogEntitiesForDenseSource(totalSize, eligibleOffsets);
    stubMappedEntitiesByOffset(source, -1);

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, null, null);

    assertThat(result.isOffsetPagination()).isTrue();
    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("elig-1", "elig-101");
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isEqualTo(102);
    verify(integrationManagerClientHelper, times(2))
        .getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any(),
            eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(), any(), anyInt(), anyInt(), any(), any(), eq(false));
    verify(catalogService, times(1))
        .getEntitiesV2(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), anyBoolean(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_ForwardNextOffsetSetsPrevOffset() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    stubEmptyCatalogEntities();
    List<EntityMappedEntityResponse> source = List.of(buildEntityMappedEntityResponse("e0", "component", "svc0"),
        buildEntityMappedEntityResponse("e1", "component", "svc1"),
        buildEntityMappedEntityResponse("e2", "component", "svc2"),
        buildEntityMappedEntityResponse("e3", "component", "svc3"));
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, null, 2);

    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("e2", "e3");
    assertThat(result.getPrevOffset()).isEqualTo(2);
    assertThat(result.getNextOffset()).isNull();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_LastPageUnderfilledWhenSourceExhausted() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    stubEmptyCatalogEntities();
    List<EntityMappedEntityResponse> source = List.of(buildEntityMappedEntityResponse("e0", "component", "svc0"),
        buildEntityMappedEntityResponse("e1", "component", "svc1"),
        buildEntityMappedEntityResponse("e2", "component", "svc2"),
        buildEntityMappedEntityResponse("e3", "component", "svc3"));
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO firstPage = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 3, null, null, null, null, null, null, null, null);
    assertThat(firstPage.getDiscoverEntitiesResponses()).hasSize(3);
    assertThat(firstPage.getNextOffset()).isEqualTo(3);
    assertThat(firstPage.getPrevOffset()).isNull();

    DiscoverEntitiesDTO lastPage = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 3, null, null, null, null, null, null, null, 3);
    assertThat(lastPage.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("e3");
    assertThat(lastPage.getNextOffset()).isNull();
    assertThat(lastPage.getPrevOffset()).isEqualTo(3);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_BackwardReturnsAscendingAndOffsets() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    stubEmptyCatalogEntities();
    List<EntityMappedEntityResponse> source = List.of(buildEntityMappedEntityResponse("e0", "component", "svc0"),
        buildEntityMappedEntityResponse("e1", "component", "svc1"),
        buildEntityMappedEntityResponse("e2", "component", "svc2"),
        buildEntityMappedEntityResponse("e3", "component", "svc3"),
        buildEntityMappedEntityResponse("e4", "component", "svc4"));
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, 4, null);

    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("e2", "e3");
    assertThat(result.getNextOffset()).isEqualTo(4);
    assertThat(result.getPrevOffset()).isEqualTo(2);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_BackwardAtBeginningWithFullPageKeepsReverseResult() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    stubEmptyCatalogEntities();
    List<EntityMappedEntityResponse> source = List.of(buildEntityMappedEntityResponse("e0", "component", "svc0"),
        buildEntityMappedEntityResponse("e1", "component", "svc1"),
        buildEntityMappedEntityResponse("e2", "component", "svc2"));
    stubMappedEntitiesByOffset(source, source.size());

    // Exactly pageLimit eligible entities before the boundary — no forward rebuild.
    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, 2, null);

    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("e0", "e1");
    assertThat(result.getNextOffset()).isEqualTo(2);
    assertThat(result.getPrevOffset()).isNull();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_ExcludesIneligibleOutOfScopeEntities() throws Exception {
    TypesIntegrationConfig config = buildHarnessCIConfig();
    config.getConfiguration().put("all_scopes", false);
    config.getConfiguration().put(
        "selected_scopes", new ArrayList<>(List.of(TEST_ACCOUNT_IDENTIFIER + "/otherOrg/otherProject")));
    stubIntegrationConfig(config);
    stubEmptyCatalogEntities();

    EntityMappedEntityResponse inScope = buildEntityMappedEntityResponse("in-scope", "component", "in-scope");
    inScope.getScope().setOrgIdentifier("otherOrg");
    inScope.getScope().setProjectIdentifier("otherProject");
    EntityMappedEntityResponse outOfScope = buildEntityMappedEntityResponse("out-scope", "component", "out-scope");
    List<EntityMappedEntityResponse> source = List.of(outOfScope, inScope);
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 10, null, null, null, null, null, null, null, null);

    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("in-scope");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_PageLimitZeroReturnsEmptyOffsetPagination() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 0, null, null, null, null, null, null, null, null);

    assertThat(result.isOffsetPagination()).isTrue();
    assertThat(result.getDiscoverEntitiesResponses()).isEmpty();
    assertThat(result.getMergeSuggestions()).isEmpty();
    verify(integrationManagerClientHelper, never())
        .getMappedEntitiesByOffset(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(),
            anyInt(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_NonCI_IgnoresNonNullOffsetsAndUsesLegacyPagePath() throws Exception {
    stubIntegrationConfig(buildNonCIConfig());
    stubEmptyCatalogEntities();
    EntityMappedEntityResponse entity = buildEntityMappedEntityResponse("cd-1", "component", "cd-svc");
    okhttp3.Headers headers = new okhttp3.Headers.Builder()
                                  .add("x-total", "7")
                                  .add("x-total-pages", "1")
                                  .add("x-page", "2")
                                  .add("x-per-page", "10")
                                  .build();
    when(integrationManagerClientHelper.getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER),
             any(), any(), eq(TEST_IDENTIFIER), any(), eq(true), any(), any(), eq(2), eq(10), any(), any(), eq(true)))
        .thenReturn(mappedEntitiesCall);
    when(mappedEntitiesCall.execute())
        .thenReturn(Response.success(new EntityMappedEntityResponseObject(List.of(entity)), headers));

    DiscoverEntitiesDTO withPrevOffset = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 2, 10, null, null, null, null, null, null, 5, null);
    DiscoverEntitiesDTO withNextOffset = catalogIntegrationService.discoverEntities(
        TEST_ACCOUNT_IDENTIFIER, null, null, TEST_IDENTIFIER, 2, 10, null, null, null, null, null, null, null, 9);

    assertThat(withPrevOffset.isOffsetPagination()).isFalse();
    assertThat(withNextOffset.isOffsetPagination()).isFalse();
    assertThat(withPrevOffset.getTotalElements()).isEqualTo(7);
    assertThat(withNextOffset.getTotalElements()).isEqualTo(7);
    assertThat(withPrevOffset.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("cd-1");
    assertThat(withNextOffset.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("cd-1");
    verify(integrationManagerClientHelper, times(2))
        .getMappedEntities(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(TEST_IDENTIFIER),
            any(), eq(true), any(), any(), eq(2), eq(10), any(), any(), eq(true));
    verify(integrationManagerClientHelper, never())
        .getMappedEntitiesByOffset(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyInt(),
            anyInt(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_BackwardRefillsAcrossMultipleWindows() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    // boundary > 100 forces a first window that does not start at offset 0, so refill must continue.
    int totalSize = 150;
    int boundary = 150;
    Set<Integer> eligibleOffsets = Set.of(40, 149);
    List<EntityMappedEntityResponse> source = buildDenseOffsetSource(totalSize, eligibleOffsets);
    stubImportedCatalogEntitiesForDenseSource(totalSize, eligibleOffsets);
    stubMappedEntitiesByOffset(source, totalSize);

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, boundary, null);

    assertThat(result.isOffsetPagination()).isTrue();
    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("elig-40", "elig-149");
    assertThat(result.getNextOffset()).isEqualTo(boundary);
    assertThat(result.getPrevOffset()).isNull();
    // First window [50,150), second window [0,50), plus hasEligibleHarnessCIEntityBefore probe(s).
    verify(integrationManagerClientHelper, times(3))
        .getMappedEntitiesByOffset(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any(),
            eq(HARNESS_CI_INTEGRATION_ID), any(), eq(true), any(), any(), anyInt(), anyInt(), any(), any(), eq(false));
    verify(catalogService, times(1))
        .getEntitiesV2(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), anyBoolean(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_ForwardBackwardRoundTripReturnsSameEligiblePage() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    EntityMappedEntityResponse imported0 = buildEntityMappedEntityResponse("imp0", "component", "imp0");
    EntityMappedEntityResponse eligibleA = buildEntityMappedEntityResponse("eligA", "component", "eligA");
    EntityMappedEntityResponse imported1 = buildEntityMappedEntityResponse("imp1", "component", "imp1");
    EntityMappedEntityResponse eligibleB = buildEntityMappedEntityResponse("eligB", "component", "eligB");
    EntityMappedEntityResponse imported2 = buildEntityMappedEntityResponse("imp2", "component", "imp2");
    EntityMappedEntityResponse eligibleC = buildEntityMappedEntityResponse("eligC", "component", "eligC");
    List<EntityMappedEntityResponse> source = List.of(imported0, eligibleA, imported1, eligibleB, imported2, eligibleC);
    stubImportedCatalogEntities("imp0", "imp1", "imp2");
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO forward = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, null, null);

    assertThat(forward.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("eligA", "eligB");
    assertThat(forward.getPrevOffset()).isNull();
    assertThat(forward.getNextOffset()).isEqualTo(4);

    DiscoverEntitiesDTO backward = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 2, null, null, null, null, null, null, forward.getNextOffset(), null);

    assertThat(backward.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("eligA", "eligB");
    assertThat(backward.getNextOffset()).isEqualTo(forward.getNextOffset());
    assertThat(backward.getPrevOffset()).isNull();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_BackwardUnderfilledRebuildsFullFirstPage() throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    EntityMappedEntityResponse imported0 = buildEntityMappedEntityResponse("imp0", "component", "imp0");
    EntityMappedEntityResponse eligibleA = buildEntityMappedEntityResponse("eligA", "component", "eligA");
    EntityMappedEntityResponse imported1 = buildEntityMappedEntityResponse("imp1", "component", "imp1");
    EntityMappedEntityResponse eligibleB = buildEntityMappedEntityResponse("eligB", "component", "eligB");
    EntityMappedEntityResponse eligibleC = buildEntityMappedEntityResponse("eligC", "component", "eligC");
    EntityMappedEntityResponse eligibleD = buildEntityMappedEntityResponse("eligD", "component", "eligD");
    List<EntityMappedEntityResponse> source = List.of(imported0, eligibleA, imported1, eligibleB, eligibleC, eligibleD);
    stubImportedCatalogEntities("imp0", "imp1");
    stubMappedEntitiesByOffset(source, source.size());

    // Only eligA exists before prevOffset=3, but enough eligible entities exist at/after the boundary.
    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 3, null, null, null, null, null, null, 3, null);

    assertThat(result.isOffsetPagination()).isTrue();
    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("eligA", "eligB", "eligC");
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isEqualTo(5);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_HarnessCI_BackwardUnderfilledExhaustedSourceRebuildsPartialFirstPage()
      throws Exception {
    stubIntegrationConfig(buildHarnessCIConfig());
    EntityMappedEntityResponse imported0 = buildEntityMappedEntityResponse("imp0", "component", "imp0");
    EntityMappedEntityResponse eligibleA = buildEntityMappedEntityResponse("eligA", "component", "eligA");
    EntityMappedEntityResponse imported1 = buildEntityMappedEntityResponse("imp1", "component", "imp1");
    EntityMappedEntityResponse eligibleB = buildEntityMappedEntityResponse("eligB", "component", "eligB");
    List<EntityMappedEntityResponse> source = List.of(imported0, eligibleA, imported1, eligibleB);
    stubImportedCatalogEntities("imp0", "imp1");
    stubMappedEntitiesByOffset(source, source.size());

    DiscoverEntitiesDTO result = catalogIntegrationService.discoverEntities(TEST_ACCOUNT_IDENTIFIER, null, null,
        HARNESS_CI_INTEGRATION_ID, 0, 3, null, null, null, null, null, null, 4, null);

    assertThat(result.isOffsetPagination()).isTrue();
    assertThat(result.getDiscoverEntitiesResponses())
        .extracting(DiscoverEntitiesResponse::getIntegrationEntityId)
        .containsExactly("eligA", "eligB");
    assertThat(result.getPrevOffset()).isNull();
    assertThat(result.getNextOffset()).isNull();
  }
}
