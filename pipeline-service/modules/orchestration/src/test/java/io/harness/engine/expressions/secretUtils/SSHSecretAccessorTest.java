/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import static io.harness.rule.OwnerRule.SIDDHARTHA;

import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.exception.EngineFunctorException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.utils.IdentifierRefHelper;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CDP)
@PrepareForTest({AmbianceUtils.class, IdentifierRefHelper.class, NGRestUtils.class})
public class SSHSecretAccessorTest extends CategoryTest {
  @Mock private SecretNGManagerClient secretNGManagerClient;
  @Mock private Call<ResponseDTO<SecretResponseWrapper>> secretCall;

  private SSHSecretAccessor sshSecretAccessor;
  private final Ambiance ambiance = Ambiance.newBuilder().build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    sshSecretAccessor = new SSHSecretAccessor(ambiance, secretNGManagerClient);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithEmptyKey() {
    Object result = sshSecretAccessor.get("");
    assertNull(result);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithValidKey_Password() {
    String secretId = "ssh-secret-123";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secretId);
      mockIdentifierRef.setAccountIdentifier("account123");
      mockIdentifierRef.setOrgIdentifier("org123");
      mockIdentifierRef.setProjectIdentifier("project123");

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secretId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response
      SecretDTOV2 secret =
          SecretDTOV2.builder().type(SecretType.SSHKey).name("Test SSH Secret").identifier(secretId).build();
      // Create SecretRefData for password
      SecretRefData passwordSecretRef =
          SecretRefData.builder().identifier("password-secret").scope(io.harness.encryption.Scope.ACCOUNT).build();

      SSHPasswordCredentialDTO passwordCredentialDTO =
          SSHPasswordCredentialDTO.builder().userName("testuser").password(passwordSecretRef).build();

      SSHConfigDTO sshConfigDTO =
          SSHConfigDTO.builder().credentialType(SSHCredentialType.Password).spec(passwordCredentialDTO).build();

      SSHAuthDTO sshAuthDTO = SSHAuthDTO.builder().type(SSHAuthScheme.SSH).spec(sshConfigDTO).build();

      SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().port(22).auth(sshAuthDTO).build();
      secret.setSpec(sshKeySpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method
      Map<String, Object> result = (Map<String, Object>) sshSecretAccessor.get(secretId);

      // Verify the result
      assertThat(result).isNotNull();
      assertThat(result.get("port")).isEqualTo("22");
      assertThat(result.get("authScheme")).isEqualTo(SSHAuthScheme.SSH.toString());
      assertThat(result.get("credType")).isEqualTo(SSHCredentialType.Password.toString());
      assertThat(result.get("username")).isEqualTo("testuser");
      assertThat(result.get("password")).isEqualTo("${{secrets.getValue('account.password-secret')}}");
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithValidKey_KeyReference() {
    String secretId = "ssh-secret-key-ref";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secretId);
      mockIdentifierRef.setAccountIdentifier("account123");
      mockIdentifierRef.setOrgIdentifier("org123");
      mockIdentifierRef.setProjectIdentifier("project123");

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secretId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response for KeyReference
      SecretDTOV2 secret =
          SecretDTOV2.builder().type(SecretType.SSHKey).name("Test SSH Key Ref Secret").identifier(secretId).build();
      // Create SecretRefData for key and passphrase
      SecretRefData keySecretRef =
          SecretRefData.builder().identifier("key-secret").scope(io.harness.encryption.Scope.PROJECT).build();

      SecretRefData passphraseSecretRef =
          SecretRefData.builder().identifier("passphrase-secret").scope(io.harness.encryption.Scope.PROJECT).build();

      SSHKeyReferenceCredentialDTO keyRefCredentialDTO = SSHKeyReferenceCredentialDTO.builder()
                                                             .userName("testuser")
                                                             .key(keySecretRef)
                                                             .encryptedPassphrase(passphraseSecretRef)
                                                             .build();

      SSHConfigDTO sshConfigDTO =
          SSHConfigDTO.builder().credentialType(SSHCredentialType.KeyReference).spec(keyRefCredentialDTO).build();

      SSHAuthDTO sshAuthDTO = SSHAuthDTO.builder().type(SSHAuthScheme.SSH).spec(sshConfigDTO).build();

      SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().port(22).auth(sshAuthDTO).build();

      secret.setSpec(sshKeySpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method
      Map<String, Object> result = (Map<String, Object>) sshSecretAccessor.get(secretId);

      // Verify the result
      assertThat(result).isNotNull();
      assertThat(result.get("port")).isEqualTo("22");
      assertThat(result.get("authScheme")).isEqualTo(SSHAuthScheme.SSH.toString());
      assertThat(result.get("credType")).isEqualTo(SSHCredentialType.KeyReference.toString());
      assertThat(result.get("username")).isEqualTo("testuser");
      assertThat(result.get("key")).isEqualTo("${{secrets.getValue('key-secret')}}");
      assertThat(result.get("passphrase")).isEqualTo("${{secrets.getValue('passphrase-secret')}}");
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithValidKey_KeyPath() {
    String secretId = "ssh-secret-key-path";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secretId);
      mockIdentifierRef.setAccountIdentifier("account123");
      mockIdentifierRef.setOrgIdentifier("org123");
      mockIdentifierRef.setProjectIdentifier("project123");

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secretId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response for KeyPath
      SecretDTOV2 secret =
          SecretDTOV2.builder().type(SecretType.SSHKey).name("Test SSH Key Path Secret").identifier(secretId).build();
      // Create SecretRefData for passphrase
      SecretRefData passphraseSecretRef =
          SecretRefData.builder().identifier("passphrase-secret").scope(io.harness.encryption.Scope.ORG).build();

      SSHKeyPathCredentialDTO keyPathCredentialDTO = SSHKeyPathCredentialDTO.builder()
                                                         .userName("testuser")
                                                         .keyPath("/path/to/key")
                                                         .encryptedPassphrase(passphraseSecretRef)
                                                         .build();

      SSHConfigDTO sshConfigDTO =
          SSHConfigDTO.builder().credentialType(SSHCredentialType.KeyPath).spec(keyPathCredentialDTO).build();

      SSHAuthDTO sshAuthDTO = SSHAuthDTO.builder().type(SSHAuthScheme.SSH).spec(sshConfigDTO).build();

      SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().port(2222).auth(sshAuthDTO).build();
      secret.setSpec(sshKeySpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method
      Map<String, Object> result = (Map<String, Object>) sshSecretAccessor.get(secretId);

      // Verify the result
      assertThat(result).isNotNull();
      assertThat(result.get("port")).isEqualTo("2222");
      assertThat(result.get("authScheme")).isEqualTo(SSHAuthScheme.SSH.toString());
      assertThat(result.get("credType")).isEqualTo(SSHCredentialType.KeyPath.toString());
      assertThat(result.get("username")).isEqualTo("testuser");
      assertThat(result.get("keyPath")).isEqualTo("/path/to/key");
      assertThat(result.get("passphrase")).isEqualTo("${{secrets.getValue('org.passphrase-secret')}}");
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithSecretError() {
    String secretId = "ssh-secret-123";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secretId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString()))
          .thenThrow(new RuntimeException("Secret not found"));

      // Test the get method
      assertThatThrownBy(() -> sshSecretAccessor.get(secretId))
          .isInstanceOf(EngineFunctorException.class)
          .hasMessageContaining("Failed to retrieve SSH secret");
    }
  }
}
