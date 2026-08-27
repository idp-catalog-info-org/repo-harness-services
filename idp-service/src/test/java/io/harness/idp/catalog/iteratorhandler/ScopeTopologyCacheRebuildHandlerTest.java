/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ScopeTopologyCacheRebuildHandlerTest extends CategoryTest {
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private CatalogScopeResolver catalogScopeResolver;
  @Mock private io.harness.idp.metrics.IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  @InjectMocks ScopeTopologyCacheRebuildHandler scopeTopologyCacheRebuildHandler;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleRebuildsForAllAccounts() {
    when(catalogEntityRepository.findDistinctAccountIdentifiers())
        .thenReturn(Arrays.asList("account1", "account2", "account3"));
    scopeTopologyCacheRebuildHandler.handle(IteratorEntity.builder().build());
    verify(catalogScopeResolver).buildScopeTopology("account1");
    verify(catalogScopeResolver).buildScopeTopology("account2");
    verify(catalogScopeResolver).buildScopeTopology("account3");
    verify(idpIteratorMetricRecorder).recordSuccess("ScopeTopologyCacheRebuildHandler", "account1");
    verify(idpIteratorMetricRecorder).recordSuccess("ScopeTopologyCacheRebuildHandler", "account2");
    verify(idpIteratorMetricRecorder).recordSuccess("ScopeTopologyCacheRebuildHandler", "account3");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleNoAccounts() {
    when(catalogEntityRepository.findDistinctAccountIdentifiers()).thenReturn(Collections.emptyList());
    scopeTopologyCacheRebuildHandler.handle(IteratorEntity.builder().build());
    verify(catalogScopeResolver, never()).buildScopeTopology(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleContinuesOnSingleAccountFailure() {
    when(catalogEntityRepository.findDistinctAccountIdentifiers()).thenReturn(Arrays.asList("account1", "account2"));
    when(catalogScopeResolver.buildScopeTopology("account1")).thenThrow(new RuntimeException("test error"));
    scopeTopologyCacheRebuildHandler.handle(IteratorEntity.builder().build());
    verify(catalogScopeResolver).buildScopeTopology("account1");
    verify(catalogScopeResolver).buildScopeTopology("account2");
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("ScopeTopologyCacheRebuildHandler", "account1");
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("ScopeTopologyCacheRebuildHandler", "account2");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleRecordsFailureOnOuterException() {
    when(catalogEntityRepository.findDistinctAccountIdentifiers()).thenThrow(new RuntimeException("db error"));
    scopeTopologyCacheRebuildHandler.handle(IteratorEntity.builder().build());
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("ScopeTopologyCacheRebuildHandler", null);
  }
}
