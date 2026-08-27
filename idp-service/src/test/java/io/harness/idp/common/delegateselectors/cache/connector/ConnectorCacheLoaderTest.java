/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.connector;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.rule.Owner;

import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ConnectorCacheLoaderTest extends CategoryTest {
  private static final String DELEGATE_SELECTOR1 = "selector1";
  private static final String DELEGATE_SELECTOR2 = "selector2";
  private static final String DELEGATE_SELECTOR3 = "selector3";
  private static final String DELEGATE_SELECTOR4 = "selector4";
  private static final String HOST1 = "host1";
  private static final String HOST2 = "host2";
  private static final String HOST3 = "host3";
  private AutoCloseable openMocks;
  @InjectMocks private ConnectorCacheLoader cacheLoader;
  @Mock private IntegrationEntityRepository integrationEntityRepository;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testLoad() {
    String accountIdentifier = "exampleAccount";
    List<IntegrationEntity> integrationEntities = new ArrayList<>();
    GithubIntegrationEntity integrationEntity1 = new GithubIntegrationEntity();
    integrationEntity1.setIntegration(IntegrationEntity.Integration.GIT);
    integrationEntity1.setHost(HOST1);
    integrationEntity1.setExecuteOnDelegate(true);
    integrationEntity1.setDelegateSelectors(new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    GithubIntegrationEntity integrationEntity2 = new GithubIntegrationEntity();
    integrationEntity2.setIntegration(IntegrationEntity.Integration.GIT);
    integrationEntity2.setHost(HOST2);
    integrationEntity2.setExecuteOnDelegate(false);
    integrationEntity2.setDelegateSelectors(new HashSet<>(Arrays.asList(DELEGATE_SELECTOR3, DELEGATE_SELECTOR4)));
    integrationEntities.add(integrationEntity1);
    integrationEntities.add(integrationEntity2);

    when(integrationEntityRepository.findByAccountIdentifier(accountIdentifier)).thenReturn(integrationEntities);

    Map<String, Set<String>> expectedHostDelegateSelectors = new HashMap<>();
    expectedHostDelegateSelectors.put(HOST1, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));

    Map<String, Set<String>> result = cacheLoader.load(accountIdentifier);

    assertEquals(expectedHostDelegateSelectors, result);
    verify(integrationEntityRepository, times(1)).findByAccountIdentifier(accountIdentifier);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
