/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl.component;

import static io.harness.ModuleType.CORE;
import static io.harness.beans.ScopeLevel.ACCOUNT;
import static io.harness.delegate.beans.connector.utils.ConnectorType.LOCAL;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_SECRETS;
import static io.harness.enforcement.constants.RestrictionType.STATIC_LIMIT;
import static io.harness.licensing.Edition.TEAM;
import static io.harness.ngsettings.SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER;
import static io.harness.rule.OwnerRule.NISHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.base.NgSecretTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.FunctionalTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.LocalConnectorDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.servicedependencies.EnforcementClient;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.licensing.Edition;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.repositories.ng.core.spring.SecretRepository;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class SecretCrudServiceImplFunctionalTest extends NgSecretTestBase {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String IDENTIFIER_1 = "Identifier_1";
  public static final String IDENTIFIER_2 = "Identifier_2";
  public static final String IDENTIFIER_3 = "Identifier_3";
  private AutoCloseable autoCloseable;
  @Inject private SecretCrudService secretCrudService;
  @Inject private EnforcementClient enforcementClient;
  @Inject private EnforcementClientConfiguration enforcementClientConfiguration;
  @Inject private SecretRepository secretRepository;
  @Inject private ScopeInfoService scopeResolverService;
  @Inject private ScopeInfoClient scopeInfoClient;
  @Inject @Named("connectorDecoratorService") private ConnectorService connectorService;
  @Inject private NGSettingsClient settingsClient;

  @Before
  public void setup() throws IOException {
    autoCloseable = MockitoAnnotations.openMocks(this);
    // Ensure scope is resolved
    when(scopeResolverService.getScopeInfo(ACCOUNT_IDENTIFIER, null, null)).thenReturn(getAccountScopeInfo());

    // Ensure secret manager is returned
    ConnectorResponseDTO harnessSM = ConnectorResponseDTO.builder()
                                         .harnessManaged(true)
                                         .connector(ConnectorInfoDTO.builder()
                                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                        .identifier("harnessSecretManager")
                                                        .connectorConfig(LocalConnectorDTO.builder().build())
                                                        .parentUniqueId(ACCOUNT_IDENTIFIER)
                                                        .connectorType(LOCAL)
                                                        .build())
                                         .build();
    ConnectorResponseDTO harnessSMGlobal = ConnectorResponseDTO.builder()
                                               .harnessManaged(true)
                                               .connector(ConnectorInfoDTO.builder()
                                                              .accountIdentifier("__GLOBAL_ACCOUNT_ID__")
                                                              .identifier("harnessSecretManager")
                                                              .connectorConfig(LocalConnectorDTO.builder().build())
                                                              .parentUniqueId("__GLOBAL_ACCOUNT_ID__")
                                                              .connectorType(LOCAL)
                                                              .build())
                                               .build();
    when(connectorService.get(getAccountScopeInfo(), "harnessSecretManager")).thenReturn(Optional.of(harnessSM));
    when(connectorService.get(getGlobalAccountScopeInfo(), "harnessSecretManager"))
        .thenReturn(Optional.of(harnessSMGlobal));

    // Ensure disable built in sm setting is resolved as false
    Call<ResponseDTO<SettingValueResponseDTO>> settingCall = mock(Call.class);
    when(settingCall.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("false").build())));
    when(settingsClient.getSetting(DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, ACCOUNT_IDENTIFIER, null, null))
        .thenReturn(settingCall);
    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .uniqueId(ACCOUNT_IDENTIFIER)
                                     .scopeType(ACCOUNT)
                                     .build();
    Call<ResponseDTO<ScopeInfo>> accountScopeCall = mock(Call.class);
    Response<ResponseDTO<ScopeInfo>> accountScopeResponse = Response.success(ResponseDTO.newResponse(accountScopeInfo));
    when(accountScopeCall.execute()).thenReturn(accountScopeResponse);
    when(accountScopeCall.clone()).thenReturn(accountScopeCall);
    doReturn(accountScopeCall).when(scopeInfoClient).getScopeInfo(eq(ACCOUNT_IDENTIFIER), any(), any());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(FunctionalTests.class)
  public void shouldNotCreateSecretWhenLimitReachesForTeamLicense() throws IOException {
    // Given a new secret to be created with two secrets in already in account and limit be 2
    ScopeInfo scopeInfo = getAccountScopeInfo();
    SecretDTOV2 secretDTO1 = getSecretDTOV2(IDENTIFIER_1);

    SecretDTOV2 secretDTO2 = getSecretDTOV2(IDENTIFIER_2);
    secretCrudService.create(scopeInfo, secretDTO1);
    secretCrudService.create(scopeInfo, secretDTO2);

    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().build();

    long limit = 2L;

    // When existing number of secret count in db is equal to limit
    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);
    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap =
        Map.of(TEAM, StaticLimitRestrictionMetadataDTO.builder().restrictionType(STATIC_LIMIT).limit(limit).build());
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = FeatureRestrictionMetadataDTO.builder()
                                                                      .name(MULTIPLE_SECRETS)
                                                                      .edition(TEAM)
                                                                      .moduleType(CORE)
                                                                      .restrictionMetadata(restrictionMetadataDTOMap)
                                                                      .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(MULTIPLE_SECRETS, ACCOUNT_IDENTIFIER)).thenReturn(call);

    // Then expect a LimitExceededException
    assertThatThrownBy(() -> secretCrudService.create(scopeInfo, secretDTOV2))
        .isInstanceOf(LimitExceededException.class);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(FunctionalTests.class)
  public void shouldCreateSecretWhenLimitDoesNotReachForTeamLicense() throws IOException {
    // Given a new secret to be created and limit be 10
    long limit = 10L;

    // When existing number of secret count in db is less than limit
    when(enforcementClientConfiguration.isEnforcementCheckEnabled()).thenReturn(true);
    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap =
        Map.of(TEAM, StaticLimitRestrictionMetadataDTO.builder().restrictionType(STATIC_LIMIT).limit(limit).build());
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = FeatureRestrictionMetadataDTO.builder()
                                                                      .name(MULTIPLE_SECRETS)
                                                                      .edition(TEAM)
                                                                      .moduleType(CORE)
                                                                      .restrictionMetadata(restrictionMetadataDTOMap)
                                                                      .build();
    Call<ResponseDTO<FeatureRestrictionMetadataDTO>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(featureRestrictionMetadataDTO)));
    when(enforcementClient.getFeatureRestrictionMetadata(MULTIPLE_SECRETS, ACCOUNT_IDENTIFIER)).thenReturn(call);

    ScopeInfo scopeInfo = getAccountScopeInfo();
    SecretDTOV2 secretDTOV2 = getSecretDTOV2(IDENTIFIER_3);

    // Then expect to create a secret
    SecretResponseWrapper secret = secretCrudService.create(scopeInfo, secretDTOV2);
    assertThat(secret).isNotNull();
  }

  private static ScopeInfo getGlobalAccountScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier("__GLOBAL_ACCOUNT_ID__")
        .scopeType(ACCOUNT)
        .uniqueId("__GLOBAL_ACCOUNT_ID__")
        .build();
  }

  private static ScopeInfo getAccountScopeInfo() {
    return ScopeInfo.builder()
        .scopeType(ACCOUNT)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .uniqueId(ACCOUNT_IDENTIFIER)
        .build();
  }

  private SecretDTOV2 getSecretDTOV2(String identifier) {
    return SecretDTOV2.builder()
        .identifier(identifier)
        .name(identifier)
        .type(SecretType.SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .value("test")
                  .valueType(ValueType.Inline)
                  .secretManagerIdentifier("account.harnessSecretManager")
                  .build())
        .build();
  }

  @After
  public void tearDown() throws Exception {
    autoCloseable.close();
  }
}
