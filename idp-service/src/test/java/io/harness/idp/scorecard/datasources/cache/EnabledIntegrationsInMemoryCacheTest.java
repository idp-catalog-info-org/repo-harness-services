/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.cache;

import static io.harness.idp.scorecard.datasources.cache.EnabledIntegrationsInMemoryCache.INTEGRATION_TYPES_TO_CHECK;
import static io.harness.rule.OwnerRule.SARABJYOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.TypesIntegrationConfig;
import io.harness.clients.integrationmanager.TypesIntegrationConfig.EnumIntegrationType;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class EnabledIntegrationsInMemoryCacheTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";

  @Mock IntegrationManagerClientHelper integrationManagerClientHelper;
  @Mock Call<List<TypesIntegrationConfig>> integrationConfigsCall;

  private EnabledIntegrationsInMemoryCache enabledIntegrationsInMemoryCache;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    enabledIntegrationsInMemoryCache = new EnabledIntegrationsInMemoryCache(integrationManagerClientHelper);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetEnabledIntegrationTypesCachesResult() throws IOException {
    stubIntegrationConfigs(List.of(integrationConfig(EnumIntegrationType.DataDog, true),
        integrationConfig(EnumIntegrationType.SonarQube, false), integrationConfig(EnumIntegrationType.GCP, true)));

    Optional<Set<EnumIntegrationType>> firstCall =
        enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(ACCOUNT_IDENTIFIER);
    Optional<Set<EnumIntegrationType>> secondCall =
        enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(ACCOUNT_IDENTIFIER);

    assertThat(firstCall).isPresent();
    assertThat(firstCall.get()).containsExactlyInAnyOrder(EnumIntegrationType.DataDog, EnumIntegrationType.GCP);
    assertThat(secondCall).isEqualTo(firstCall);
    verify(integrationManagerClientHelper, times(1))
        .listIntegrationConfigs(
            eq(ACCOUNT_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(INTEGRATION_TYPES_TO_CHECK), eq(true), eq(true));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetEnabledIntegrationTypesFailsOpenOnError() throws IOException {
    when(integrationManagerClientHelper.listIntegrationConfigs(
             eq(ACCOUNT_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(INTEGRATION_TYPES_TO_CHECK), eq(true), eq(true)))
        .thenReturn(integrationConfigsCall);
    when(integrationConfigsCall.execute()).thenThrow(new IOException("integration-manager unavailable"));

    Optional<Set<EnumIntegrationType>> enabledTypes =
        enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(ACCOUNT_IDENTIFIER);

    assertThat(enabledTypes).isEmpty();
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetEnabledIntegrationTypesDoesNotCacheFailures() throws IOException {
    when(integrationManagerClientHelper.listIntegrationConfigs(
             eq(ACCOUNT_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(INTEGRATION_TYPES_TO_CHECK), eq(true), eq(true)))
        .thenReturn(integrationConfigsCall);
    AtomicBoolean shouldFail = new AtomicBoolean(true);
    when(integrationConfigsCall.execute()).thenAnswer(invocation -> {
      if (shouldFail.get()) {
        throw new IOException("integration-manager unavailable");
      }
      return Response.success(List.of(integrationConfig(EnumIntegrationType.DataDog, true)));
    });

    Optional<Set<EnumIntegrationType>> firstCall =
        enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(ACCOUNT_IDENTIFIER);
    shouldFail.set(false);
    Optional<Set<EnumIntegrationType>> secondCall =
        enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(ACCOUNT_IDENTIFIER);

    assertThat(firstCall).isEmpty();
    assertThat(secondCall).isPresent();
    assertThat(secondCall.get()).containsExactly(EnumIntegrationType.DataDog);
    verify(integrationManagerClientHelper, times(2))
        .listIntegrationConfigs(
            eq(ACCOUNT_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(INTEGRATION_TYPES_TO_CHECK), eq(true), eq(true));
  }

  private void stubIntegrationConfigs(List<TypesIntegrationConfig> integrationConfigs) throws IOException {
    when(integrationManagerClientHelper.listIntegrationConfigs(
             eq(ACCOUNT_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(INTEGRATION_TYPES_TO_CHECK), eq(true), eq(true)))
        .thenReturn(integrationConfigsCall);
    when(integrationConfigsCall.execute()).thenReturn(Response.success(integrationConfigs));
  }

  private TypesIntegrationConfig integrationConfig(EnumIntegrationType integrationType, boolean enabled) {
    TypesIntegrationConfig integrationConfig = new TypesIntegrationConfig();
    integrationConfig.setIntegrationType(integrationType);
    integrationConfig.setEnabled(enabled);
    return integrationConfig;
  }
}
