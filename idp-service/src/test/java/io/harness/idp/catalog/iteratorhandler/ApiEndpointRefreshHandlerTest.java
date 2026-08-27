/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.processor.ApiSpecGitRefresher;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.iterators.config.ApiEndpointRefreshIteratorConfig;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointRefreshHandlerTest extends CategoryTest {
  private static final String ACCOUNT_A = "account-A";
  private static final String ACCOUNT_B = "account-B";

  AutoCloseable openMocks;

  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock ApiEndpointProcessor apiEndpointProcessor;
  @Mock IdpCommonService idpCommonService;
  @Mock IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  @Mock ApiSpecGitRefresher apiSpecGitRefresher;

  @InjectMocks @Spy ApiEndpointRefreshHandler handler;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_emptyPage_doesNothing() {
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(Collections.emptyList());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, never()).processEntity(any());
    verify(idpCommonService, never()).idpApiEndpointExtractionEnabled(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_ffOn_processesEntity() {
    CatalogEntity e1 = apiEntity(ACCOUNT_A, "api-1");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(e1));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(e1)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(1)).processEntity(e1);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("ApiEndpointRefreshHandler", ACCOUNT_A);
    verify(idpIteratorMetricRecorder, never()).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_ffOff_skipsEntity() {
    CatalogEntity e1 = apiEntity(ACCOUNT_A, "api-1");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(e1));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(false);

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, never()).processEntity(any());
    verify(idpIteratorMetricRecorder, never()).recordFailure(any(), any());
    verify(idpIteratorMetricRecorder, never()).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_ffServiceThrows_treatsAsOff() {
    CatalogEntity e1 = apiEntity(ACCOUNT_A, "api-1");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(e1));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A))
        .thenThrow(new RuntimeException("FF service down"));

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, never()).processEntity(any());
    verify(idpIteratorMetricRecorder, never()).recordSuccess(any(), any());
    verify(idpIteratorMetricRecorder, never()).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_ffCheckedOncePerAccount_notPerEntity() {
    CatalogEntity a1 = apiEntity(ACCOUNT_A, "api-1");
    CatalogEntity a2 = apiEntity(ACCOUNT_A, "api-2");
    CatalogEntity a3 = apiEntity(ACCOUNT_A, "api-3");
    CatalogEntity b1 = apiEntity(ACCOUNT_B, "api-4");
    CatalogEntity b2 = apiEntity(ACCOUNT_B, "api-5");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(a1, a2, a3, b1, b2));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_B)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(any())).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(idpCommonService, times(1)).idpApiEndpointExtractionEnabled(ACCOUNT_A);
    verify(idpCommonService, times(1)).idpApiEndpointExtractionEnabled(ACCOUNT_B);
    verify(apiEndpointProcessor, times(5)).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_mixedAccounts_onlyFfOnProcessed() {
    CatalogEntity a1 = apiEntity(ACCOUNT_A, "api-1");
    CatalogEntity b1 = apiEntity(ACCOUNT_B, "api-2");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(a1, b1));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_B)).thenReturn(false);
    when(apiEndpointProcessor.processEntity(a1)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(1)).processEntity(a1);
    verify(apiEndpointProcessor, never()).processEntity(b1);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_oneEntityThrows_othersStillProcessed() {
    CatalogEntity good1 = apiEntity(ACCOUNT_A, "good-1");
    CatalogEntity bad = apiEntity(ACCOUNT_A, "bad");
    CatalogEntity good2 = apiEntity(ACCOUNT_A, "good-2");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(good1, bad, good2));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(good1)).thenReturn(emptySuccess());
    doThrow(new RuntimeException("boom")).when(apiEndpointProcessor).processEntity(bad);
    when(apiEndpointProcessor.processEntity(good2)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(1)).processEntity(good1);
    verify(apiEndpointProcessor, times(1)).processEntity(bad);
    verify(apiEndpointProcessor, times(1)).processEntity(good2);
    verify(idpIteratorMetricRecorder, times(2)).recordSuccess("ApiEndpointRefreshHandler", ACCOUNT_A);
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("ApiEndpointRefreshHandler", ACCOUNT_A);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_nonApiEntity_defenseInDepthSkip() {
    CatalogEntity nonApi =
        InlineCatalogEntity.builder().accountIdentifier(ACCOUNT_A).kind("component").identifier("svc-1").build();
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(nonApi));

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, never()).processEntity(any());
    verify(idpCommonService, never()).idpApiEndpointExtractionEnabled(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_entityCheckedWithinWindow_skipped() {
    long now = System.currentTimeMillis();
    CatalogEntity recent = apiEntityWithLastCheckedAt(ACCOUNT_A, "fresh", now - Duration.ofHours(1).toMillis());
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(recent));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, never()).processEntity(any());
    verify(idpIteratorMetricRecorder, never()).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_entityCheckedOutsideWindow_processed() {
    long now = System.currentTimeMillis();
    CatalogEntity stale = apiEntityWithLastCheckedAt(ACCOUNT_A, "stale", now - Duration.ofHours(7).toMillis());
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(stale));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(stale)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(1)).processEntity(stale);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_entityNeverChecked_processed() {
    CatalogEntity neverChecked = apiEntity(ACCOUNT_A, "fresh-import");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(neverChecked));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(neverChecked)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(1)).processEntity(neverChecked);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_mixedRecency_onlyStaleProcessed() {
    long now = System.currentTimeMillis();
    CatalogEntity recent = apiEntityWithLastCheckedAt(ACCOUNT_A, "recent", now - Duration.ofHours(1).toMillis());
    CatalogEntity stale = apiEntityWithLastCheckedAt(ACCOUNT_A, "stale", now - Duration.ofHours(8).toMillis());
    CatalogEntity neverChecked = apiEntity(ACCOUNT_A, "never");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(recent, stale, neverChecked));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(any())).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, never()).processEntity(recent);
    verify(apiEndpointProcessor, times(1)).processEntity(stale);
    verify(apiEndpointProcessor, times(1)).processEntity(neverChecked);
  }

  // ---------------------------------------------------------------------------
  // Per-fire processing cap — bounds network IO when many entities are due.
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_capLimitsCallsPerFire() {
    int pageCount = ApiEndpointRefreshHandler.DEFAULT_MAX_PROCESS_CALLS_PER_FIRE + 5;
    List<CatalogEntity> page = new ArrayList<>();
    for (int i = 0; i < pageCount; i++) {
      page.add(apiEntity(ACCOUNT_A, "api-" + i));
    }
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(page);
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(any())).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(ApiEndpointRefreshHandler.DEFAULT_MAX_PROCESS_CALLS_PER_FIRE))
        .processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_recencySkippedEntitiesDoNotCountAgainstCap() {
    long now = System.currentTimeMillis();
    List<CatalogEntity> page = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      page.add(apiEntityWithLastCheckedAt(ACCOUNT_A, "recent-" + i, now - Duration.ofMinutes(30).toMillis()));
    }
    for (int i = 0; i < 3; i++) {
      page.add(apiEntityWithLastCheckedAt(ACCOUNT_A, "stale-" + i, now - Duration.ofHours(10).toMillis()));
    }
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(page);
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(any())).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiEndpointProcessor, times(3)).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testFetchStaleApiEntitiesPage_queryPushesKindAndRecency() {
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(Collections.emptyList());

    long beforeMillis = System.currentTimeMillis();
    handler.fetchStaleApiEntitiesPage();
    long afterMillis = System.currentTimeMillis();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(CatalogEntity.class));
    Query captured = queryCaptor.getValue();

    Document filter = captured.getQueryObject();
    List<?> andClauses = (List<?>) filter.get("$and");
    assertThat(andClauses).hasSize(2);

    Document kindClause = (Document) andClauses.get(0);
    assertThat(kindClause.get("kind")).isEqualTo("api");

    Document recencyClause = (Document) andClauses.get(1);
    List<?> orClauses = (List<?>) recencyClause.get("$or");
    assertThat(orClauses).hasSize(2);

    Document existsBranch = (Document) orClauses.get(0);
    Document existsSpec = (Document) existsBranch.get("decorator._processed_data.metadata.apis.lastCheckedAt");
    assertThat(existsSpec.get("$exists")).isEqualTo(false);

    Document ltBranch = (Document) orClauses.get(1);
    Document ltSpec = (Document) ltBranch.get("decorator._processed_data.metadata.apis.lastCheckedAt");
    long cutoff = ((Number) ltSpec.get("$lt")).longValue();
    long recencyWindowMillis = Duration.ofSeconds(ApiEndpointRefreshHandler.DEFAULT_RECENCY_WINDOW_SECONDS).toMillis();
    assertThat(cutoff).isBetween(beforeMillis - recencyWindowMillis, afterMillis - recencyWindowMillis);

    assertThat(captured.getLimit()).isEqualTo(ApiEndpointRefreshHandler.DEFAULT_PAGE_SIZE);
    assertThat(captured.getFieldsObject().get("decorator._processed_data.metadata.apis.paths")).isEqualTo(0);
    Document sortObject = captured.getSortObject();
    assertThat(sortObject.get("decorator._processed_data.metadata.apis.lastCheckedAt")).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testReadLastCheckedAt_nullForMissingDecorator() {
    CatalogEntity e = apiEntity(ACCOUNT_A, "x");
    e.setDecorator(null);
    org.assertj.core.api.Assertions.assertThat(ApiEndpointRefreshHandler.readLastCheckedAt(e)).isNull();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testReadLastCheckedAt_nullForMalformedPath() {
    CatalogEntity e = apiEntity(ACCOUNT_A, "x");
    Map<String, Object> decorator = new HashMap<>();
    decorator.put("_processed_data", "not-a-map");
    e.setDecorator(decorator);
    org.assertj.core.api.Assertions.assertThat(ApiEndpointRefreshHandler.readLastCheckedAt(e)).isNull();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testReadLastCheckedAt_returnsValue() {
    long expected = 1700000000000L;
    CatalogEntity e = apiEntityWithLastCheckedAt(ACCOUNT_A, "x", expected);
    org.assertj.core.api.Assertions.assertThat(ApiEndpointRefreshHandler.readLastCheckedAt(e)).isEqualTo(expected);
  }

  // Git placeholder refresh — the fetch/merge/swallow logic itself now lives in
  // ApiSpecGitRefresherTest; these tests only cover the handler's wiring: it delegates to the
  // refresher with propagateErrors=false, in the right order, under the IDP service principal.

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_placeholderEntity_refreshesGitBeforeProcessing() {
    CatalogEntity placeholder = placeholderApiEntity(ACCOUNT_A, "git-api", "$yaml");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(placeholder));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(placeholder)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(apiSpecGitRefresher, apiEndpointProcessor);
    inOrder.verify(apiSpecGitRefresher).refresh(placeholder, false);
    inOrder.verify(apiEndpointProcessor).processEntity(placeholder);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_nonPlaceholderEntity_doesNotFetchGit() {
    CatalogEntity bareUrl = apiEntity(ACCOUNT_A, "url-api");
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("definition", "https://petstore.swagger.io/v2/swagger.json");
    bareUrl.setSpec(spec);
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(bareUrl));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(bareUrl)).thenReturn(emptySuccess());

    handler.handle(IteratorEntity.builder().build());

    verify(apiSpecGitRefresher, never()).refresh(any(), anyBoolean());
    verify(apiEndpointProcessor, times(1)).processEntity(bareUrl);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHandle_placeholderEntity_setsServicePrincipalDuringRefreshAndRestoresBeforeProcessing() {
    // processOne runs on a pooled worker thread, so we can't seed/observe a "previous" principal
    // from the test's main thread; instead we assert set+restore within that worker thread's own
    // lifecycle: ServicePrincipal is visible during the refresh call, and is gone again (restored
    // to this fresh thread's null baseline) by the time processEntity is called right after.
    CatalogEntity placeholder = placeholderApiEntity(ACCOUNT_A, "git-api", "$json");
    when(handler.fetchStaleApiEntitiesPage()).thenReturn(List.of(placeholder));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_A)).thenReturn(true);

    AtomicReference<Principal> principalDuringRefresh = new AtomicReference<>();
    doAnswer(invocation -> {
      principalDuringRefresh.set(SourcePrincipalContextBuilder.getSourcePrincipal());
      return null;
    })
        .when(apiSpecGitRefresher)
        .refresh(eq(placeholder), eq(false));

    AtomicReference<Principal> principalDuringProcessing = new AtomicReference<>();
    when(apiEndpointProcessor.processEntity(placeholder)).thenAnswer(invocation -> {
      principalDuringProcessing.set(SourcePrincipalContextBuilder.getSourcePrincipal());
      return emptySuccess();
    });

    handler.handle(IteratorEntity.builder().build());

    assertThat(principalDuringRefresh.get()).isInstanceOf(ServicePrincipal.class);
    assertThat(principalDuringRefresh.get().getName()).isEqualTo(AuthorizationServiceHeader.IDP_SERVICE.getServiceId());
    assertThat(principalDuringProcessing.get()).isNull();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(ApiEndpointRefreshIteratorConfig.builder()
                                  .enabled(true)
                                  .targetIntervalInSeconds(3600)
                                  .recencyWindowInSeconds(21600)
                                  .build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ApiEndpointRefreshHandler.class), any());
  }

  private static CatalogEntity apiEntity(String accountId, String identifier) {
    return InlineCatalogEntity.builder().accountIdentifier(accountId).kind("api").identifier(identifier).build();
  }

  private static CatalogEntity placeholderApiEntity(String accountId, String identifier, String placeholderKey) {
    CatalogEntity entity = apiEntity(accountId, identifier);
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put(placeholderKey, "https://github.com/my-org/specs/blob/main/openapi.yaml");
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("definition", definition);
    entity.setSpec(spec);
    entity.setYaml("apiVersion: harness.io/v1\nkind: API\nidentifier: " + identifier + "\nspec:\n  definition:\n    "
        + placeholderKey + ": https://github.com/my-org/specs/blob/main/openapi.yaml\n");
    return entity;
  }

  private static CatalogEntity apiEntityWithLastCheckedAt(String accountId, String identifier, long lastCheckedAt) {
    CatalogEntity entity = apiEntity(accountId, identifier);
    Map<String, Object> apis = new LinkedHashMap<>();
    apis.put("lastCheckedAt", lastCheckedAt);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("apis", apis);
    Map<String, Object> processedData = new LinkedHashMap<>();
    processedData.put("metadata", metadata);
    Map<String, Object> decorator = new HashMap<>();
    decorator.put("_processed_data", processedData);
    entity.setDecorator(decorator);
    return entity;
  }

  private static ProcessingOutcome emptySuccess() {
    return ProcessingOutcome.success(false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }
}
