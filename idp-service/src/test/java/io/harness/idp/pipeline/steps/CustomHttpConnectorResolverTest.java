/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.steps;

import static io.harness.rule.OwnerRule.ARYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.CustomHttpConnectorDTO;
import io.harness.delegate.beans.connector.customhttp.ApiTokenAuthDTO;
import io.harness.delegate.beans.connector.customhttp.BasicAuthDTO;
import io.harness.delegate.beans.connector.customhttp.CustomHttpAuthType;
import io.harness.delegate.beans.connector.customhttp.CustomHttpAuthenticationDTO;
import io.harness.delegate.beans.connector.customhttp.CustomHttpHeaderKeyAndValue;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.NGLogCallback;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class CustomHttpConnectorResolverTest extends CategoryTest {
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private NGLogCallback logCallback;

  private CustomHttpConnectorResolver resolver;
  private AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    resolver = new CustomHttpConnectorResolver(connectorResourceClient, ngSecretService, decryptionHelper);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void resolve_failsWhenConnectorRefMissing() {
    assertThatThrownBy(() -> resolver.resolve(null, "acct", null, null, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("missing connectorRef");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void resolve_apiTokenAuthBuildsAuthorizationHeader() throws Exception {
    SecretRefData token =
        SecretRefData.builder().identifier("secret-id").decryptedValue("abc123".toCharArray()).build();
    ApiTokenAuthDTO apiToken =
        ApiTokenAuthDTO.builder().headerName("X-Api-Token").headerPrefix("Bearer ").tokenRef(token).build();
    CustomHttpConnectorDTO connector = CustomHttpConnectorDTO.builder()
                                           .baseUrl("https://api.example.com")
                                           .authentication(CustomHttpAuthenticationDTO.builder()
                                                               .authType(CustomHttpAuthType.API_TOKEN)
                                                               .credentials(apiToken)
                                                               .build())
                                           .delegateSelectors(new HashSet<>(List.of("eu-1")))
                                           .build();
    stubConnectorFetch(connector);
    when(decryptionHelper.decrypt(any(DecryptableEntity.class), anyList())).thenReturn(apiToken);

    ActionStepHelper.ResolvedConnector resolved = resolver.resolve("account.my_conn", "acct", null, null, logCallback);

    assertThat(resolved.getBaseUrl()).isEqualTo("https://api.example.com");
    assertThat(resolved.getAuthHeaders()).containsEntry("X-Api-Token", "Bearer abc123");
    assertThat(resolved.getDelegateSelectors()).containsExactly("eu-1");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void resolve_basicAuthBuildsRfc7617AuthorizationHeader() throws Exception {
    SecretRefData password = SecretRefData.builder().identifier("pwd").decryptedValue("s3cret".toCharArray()).build();
    BasicAuthDTO basic = BasicAuthDTO.builder().username("alice").passwordRef(password).build();
    CustomHttpConnectorDTO connector = CustomHttpConnectorDTO.builder()
                                           .baseUrl("https://example.com")
                                           .authentication(CustomHttpAuthenticationDTO.builder()
                                                               .authType(CustomHttpAuthType.BASIC_AUTH)
                                                               .credentials(basic)
                                                               .build())
                                           .build();
    stubConnectorFetch(connector);
    when(decryptionHelper.decrypt(any(DecryptableEntity.class), anyList())).thenReturn(basic);

    ActionStepHelper.ResolvedConnector resolved = resolver.resolve("account.my_conn", "acct", null, null, logCallback);

    String expectedAuth =
        "Basic " + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(resolved.getAuthHeaders()).containsEntry("Authorization", expectedAuth);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void resolve_additionalHeadersMergeIntoDefaults() throws Exception {
    SecretRefData encryptedSecret =
        SecretRefData.builder().identifier("sec").decryptedValue("topsecret".toCharArray()).build();
    CustomHttpConnectorDTO connector =
        CustomHttpConnectorDTO.builder()
            .baseUrl("https://example.com")
            .authentication(
                CustomHttpAuthenticationDTO.builder().authType(CustomHttpAuthType.NO_AUTH).credentials(null).build())
            .additionalHeaders(List.of(
                CustomHttpHeaderKeyAndValue.builder().key("X-Plain").value("plain-val").isValueEncrypted(false).build(),
                CustomHttpHeaderKeyAndValue.builder()
                    .key("X-Secret")
                    .isValueEncrypted(true)
                    .encryptedValueRef(encryptedSecret)
                    .build()))
            .build();
    stubConnectorFetch(connector);
    when(decryptionHelper.decrypt(any(DecryptableEntity.class), anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ActionStepHelper.ResolvedConnector resolved = resolver.resolve("account.my_conn", "acct", null, null, logCallback);

    assertThat(resolved.getDefaultHeaders()).containsEntry("X-Plain", "plain-val");
    assertThat(resolved.getDefaultHeaders()).containsEntry("X-Secret", "topsecret");
    assertThat(resolved.getAuthHeaders()).isEmpty();
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void resolve_throwsWhenConnectorNotFound() throws Exception {
    @SuppressWarnings("unchecked")
    Call<io.harness.ng.core.dto.ResponseDTO<Optional<ConnectorDTO>>> call = org.mockito.Mockito.mock(Call.class);
    when(connectorResourceClient.get(anyString(), anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(io.harness.ng.core.dto.ResponseDTO.newResponse(Optional.empty())));

    assertThatThrownBy(() -> resolver.resolve("account.missing", "acct", null, null, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not found");
  }

  @SuppressWarnings("unchecked")
  private void stubConnectorFetch(CustomHttpConnectorDTO connector) throws Exception {
    ConnectorInfoDTO info =
        ConnectorInfoDTO.builder().identifier("my_conn").name("My Connector").connectorConfig(connector).build();
    ConnectorDTO dto = ConnectorDTO.builder().connectorInfo(info).build();
    Call<io.harness.ng.core.dto.ResponseDTO<Optional<ConnectorDTO>>> call = org.mockito.Mockito.mock(Call.class);
    when(connectorResourceClient.get(anyString(), anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(io.harness.ng.core.dto.ResponseDTO.newResponse(Optional.of(dto))));
  }
}
