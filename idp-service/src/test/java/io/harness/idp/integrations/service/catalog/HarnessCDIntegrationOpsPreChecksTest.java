/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.catalog;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.beans.catalog.HarnessCDIntegrationSyncRequest;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class HarnessCDIntegrationOpsPreChecksTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";

  @InjectMocks HarnessCDIntegrationOpsImpl harnessCDIntegrationOps;
  @Mock IdpCommonService idpCommonService;
  @Mock NamespaceService namespaceService;
  @Mock IntegrationEntityRepository integrationEntityRepository;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  private HarnessCDIntegrationSyncRequest buildRequest() {
    return HarnessCDIntegrationSyncRequest.builder().accountIdentifier(ACCOUNT_ID).scope("account.*").build();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testPreChecks_IntOn_ReturnsFalse() {
    when(idpCommonService.idpIntegrationsEnabled(ACCOUNT_ID)).thenReturn(true);

    boolean result = harnessCDIntegrationOps.preChecks(buildRequest());

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testPreChecks_IntOff_CdAdOn_ReturnsTrue() {
    when(idpCommonService.idpIntegrationsEnabled(ACCOUNT_ID)).thenReturn(false);
    when(namespaceService.getAccountIdpStatus(ACCOUNT_ID)).thenReturn(true);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(true);
    when(idpCommonService.idpCatalogCDAutoDiscoveryEnabled(ACCOUNT_ID)).thenReturn(true);

    boolean result = harnessCDIntegrationOps.preChecks(buildRequest());

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testPreChecks_IntOff_CdAdOff_ReturnsFalse() {
    when(idpCommonService.idpIntegrationsEnabled(ACCOUNT_ID)).thenReturn(false);
    when(namespaceService.getAccountIdpStatus(ACCOUNT_ID)).thenReturn(true);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(true);
    when(idpCommonService.idpCatalogCDAutoDiscoveryEnabled(ACCOUNT_ID)).thenReturn(false);

    boolean result = harnessCDIntegrationOps.preChecks(buildRequest());

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testPreChecks_NamespaceInactive_ReturnsFalse() {
    when(idpCommonService.idpIntegrationsEnabled(ACCOUNT_ID)).thenReturn(false);
    when(namespaceService.getAccountIdpStatus(ACCOUNT_ID)).thenReturn(false);

    boolean result = harnessCDIntegrationOps.preChecks(buildRequest());

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testPreChecks_V2Disabled_ReturnsFalse() {
    when(idpCommonService.idpIntegrationsEnabled(ACCOUNT_ID)).thenReturn(false);
    when(namespaceService.getAccountIdpStatus(ACCOUNT_ID)).thenReturn(true);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);

    boolean result = harnessCDIntegrationOps.preChecks(buildRequest());

    assertThat(result).isFalse();
  }
}
