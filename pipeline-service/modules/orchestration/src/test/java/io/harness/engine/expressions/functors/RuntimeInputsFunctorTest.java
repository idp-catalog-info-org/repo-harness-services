/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.ngsettings.SettingIdentifiers.CI_CACHE_CONNECTOR;
import static io.harness.rule.OwnerRule.SATYAKOTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import retrofit2.Call;

public class RuntimeInputsFunctorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "proj1";
  private static final String CONNECTOR_ID = "testGCPConnectorzUHPuXK7Wu";
  private static final String ACCOUNT_SCOPED_REF = "account." + CONNECTOR_ID;
  private static final String ORG_SCOPED_REF = "org." + CONNECTOR_ID;

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldResolveAccountScopedCacheConnectorAndSetGcsBackend() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(ACCOUNT_SCOPED_REF).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), isNull(), isNull())).thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.of(connectorDto(ConnectorType.GCP)));

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("gcs");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), isNull(), isNull());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldResolveProjectScopedCacheConnectorAndSetS3Backend() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(CONNECTOR_ID).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.of(connectorDto(ConnectorType.AWS)));

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("s3");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID));
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldResolveProjectScopedAzureCacheConnectorAndSetAzureBackend() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(CONNECTOR_ID).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.of(connectorDto(ConnectorType.AZURE)));

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("azure");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID));
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldResolveOrgScopedCacheConnectorAndSetGcsBackend() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(ORG_SCOPED_REF).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull()))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.of(connectorDto(ConnectorType.GCP)));

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("gcs");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldResolveOrgScopedCacheConnectorAndSetS3Backend() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(ORG_SCOPED_REF).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull()))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.of(connectorDto(ConnectorType.AWS)));

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("s3");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldResolveOrgScopedAzureCacheConnectorAndSetAzureBackend() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(ORG_SCOPED_REF).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull()))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.of(connectorDto(ConnectorType.AZURE)));

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("azure");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldFallBackToS3WhenProjectScopedCacheConnectorNotFound() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(CONNECTOR_ID).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID)))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.empty());

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("s3");
    }
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void shouldFallBackToS3WhenOrgScopedCacheConnectorNotFound() {
    NGSettingsClient settingsClient = Mockito.mock(NGSettingsClient.class);
    ConnectorResourceClient connectorResourceClient = Mockito.mock(ConnectorResourceClient.class);
    RuntimeInputsFunctor functor = functorWithInjectedClients(settingsClient, connectorResourceClient);

    Call<ResponseDTO<SettingValueResponseDTO>> settingsCall = Mockito.mock(Call.class);
    SettingValueResponseDTO settingValue =
        SettingValueResponseDTO.builder().valueType(SettingValueType.STRING).value(ORG_SCOPED_REF).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> connectorCall = Mockito.mock(Call.class);

    when(settingsClient.getSetting(CI_CACHE_CONNECTOR, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(settingsCall);

    try (var ng = Mockito.mockStatic(NGRestUtils.class)) {
      ng.when(() -> NGRestUtils.getResponse(settingsCall)).thenReturn(settingValue);
      when(connectorResourceClient.get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull()))
          .thenReturn(connectorCall);
      ng.when(() -> NGRestUtils.getResponse(connectorCall)).thenReturn(Optional.empty());

      assertThat(functor.get(RuntimeInputsFunctor.BACKEND)).isEqualTo("s3");
    }

    verify(connectorResourceClient).get(eq(CONNECTOR_ID), eq(ACCOUNT_ID), eq(ORG_ID), isNull());
  }

  private static RuntimeInputsFunctor functorWithInjectedClients(
      NGSettingsClient settingsClient, ConnectorResourceClient connectorResourceClient) {
    RuntimeInputsFunctor functor = RuntimeInputsFunctor.builder().ambiance(baseAmbiance()).build();
    on(functor).set("settingsClient", settingsClient);
    on(functor).set("connectorResourceClient", connectorResourceClient);
    on(functor).set("featureFlagService", Mockito.mock(PmsFeatureFlagService.class));
    return functor;
  }

  private static Ambiance baseAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .putSetupAbstractions("orgIdentifier", ORG_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_ID)
        .build();
  }

  private static ConnectorDTO connectorDto(ConnectorType connectorType) {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name(connectorType.name().toLowerCase())
                           .identifier(CONNECTOR_ID)
                           .connectorType(connectorType)
                           .connectorConfig(Mockito.mock(ConnectorConfigDTO.class))
                           .build())
        .build();
  }
}
