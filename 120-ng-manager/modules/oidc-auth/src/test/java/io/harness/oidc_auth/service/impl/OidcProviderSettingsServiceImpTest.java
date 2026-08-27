/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc_auth.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NIYASHA;
import static io.harness.rule.OwnerRule.TEJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ds.remote.DSEventPublishHelper;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.oidc_auth.entity.OidcProviderSettings;
import io.harness.repositories.OidcProviderRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.OidcProviderDTO;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class OidcProviderSettingsServiceImpTest extends CategoryTest {
  @Mock private OidcProviderRepository oidcProviderRepository;
  @InjectMocks private OidcProviderServiceImpl oidcProviderService;
  @Mock EnforcementClientService enforcementClientService;
  @Mock private DSEventPublishHelper dsEventPublishHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String IDENTIFIER = "oidc1";

  private OidcProviderSettings oidcProviderSettingsEntity;
  private OidcProviderDTO oidcProviderDTO;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    oidcProviderSettingsEntity = new OidcProviderSettings();
    oidcProviderSettingsEntity.setIdentifier(IDENTIFIER);

    oidcProviderDTO = new OidcProviderDTO();
    oidcProviderDTO.setIdentifier(IDENTIFIER);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateOidcProvider_Success() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.empty());
    when(oidcProviderRepository.save(any())).thenReturn(oidcProviderSettingsEntity);

    OidcProviderDTO result = oidcProviderService.createOidcProvider(ACCOUNT_ID, oidcProviderDTO);

    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo(IDENTIFIER);
    verify(oidcProviderRepository, times(1)).save(any());
    verify(dsEventPublishHelper, times(1)).publishAuthUpdateEventToDS(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateOidcProvider_AlreadyExists() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.of(oidcProviderSettingsEntity));

    assertThatThrownBy(() -> oidcProviderService.createOidcProvider(ACCOUNT_ID, oidcProviderDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format("Oidc Provider with identifier %s already exists.", IDENTIFIER));
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateOidcProvider_InternalServerError() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.empty());
    when(oidcProviderRepository.save(any())).thenThrow(new RuntimeException("DB error"));

    assertThatThrownBy(() -> oidcProviderService.createOidcProvider(ACCOUNT_ID, oidcProviderDTO))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed to create the OIDC provider, please try again.");
    verify(dsEventPublishHelper, never()).publishAuthUpdateEventToDS(anyString());
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testUpdateOidcProvider_PublishesAuthUpdateEventToDS() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.of(oidcProviderSettingsEntity));
    when(oidcProviderRepository.save(any())).thenReturn(oidcProviderSettingsEntity);

    OidcProviderDTO result = oidcProviderService.updateOidcProvider(ACCOUNT_ID, IDENTIFIER, oidcProviderDTO);

    assertThat(result).isNotNull();
    verify(oidcProviderRepository, times(1)).save(any());
    verify(dsEventPublishHelper, times(1)).publishAuthUpdateEventToDS(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testUpdateOidcProvider_DoesNotPublishAuthUpdateEventToDSOnFailure() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.of(oidcProviderSettingsEntity));
    when(oidcProviderRepository.save(any())).thenThrow(new RuntimeException("DB error"));

    assertThatThrownBy(() -> oidcProviderService.updateOidcProvider(ACCOUNT_ID, IDENTIFIER, oidcProviderDTO))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed to update the OIDC provider, please try again.");
    verify(dsEventPublishHelper, never()).publishAuthUpdateEventToDS(anyString());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetOidcProvider_Success() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.of(oidcProviderSettingsEntity));

    OidcProviderDTO result = oidcProviderService.getOidcProvider(ACCOUNT_ID, IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo(IDENTIFIER);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetOidcProvider_NotFound() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.empty());

    OidcProviderDTO result = oidcProviderService.getOidcProvider(ACCOUNT_ID, IDENTIFIER);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteOidcProvider_Success() {
    when(oidcProviderRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER))
        .thenReturn(Optional.empty());
    doNothing().when(oidcProviderRepository).deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER);

    boolean result = oidcProviderService.deleteOidcProvider(ACCOUNT_ID, IDENTIFIER);

    assertThat(result).isTrue();
    verify(oidcProviderRepository, times(1)).deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER);
    verify(dsEventPublishHelper, times(1)).publishAuthUpdateEventToDS(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteOidcProvider_InternalServerError() {
    doThrow(new RuntimeException("DB error"))
        .when(oidcProviderRepository)
        .deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, IDENTIFIER);

    assertThatThrownBy(() -> oidcProviderService.deleteOidcProvider(ACCOUNT_ID, IDENTIFIER))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed to delete the OIDC provider, please try again.");
    verify(dsEventPublishHelper, never()).publishAuthUpdateEventToDS(anyString());
  }
}
