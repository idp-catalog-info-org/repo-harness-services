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
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.WinRmAuthScheme;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.utils.IdentifierRefHelper;

import java.util.ArrayList;
import java.util.List;
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
public class WinrmSecretAccessorTest extends CategoryTest {
  @Mock private SecretNGManagerClient secretNGManagerClient;
  @Mock private Call<ResponseDTO<SecretResponseWrapper>> secretCall;

  private WinrmSecretAccessor winrmSecretAccessor;
  private final Ambiance ambiance = Ambiance.newBuilder().build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    winrmSecretAccessor = new WinrmSecretAccessor(ambiance, secretNGManagerClient);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithEmptyKey() {
    Object result = winrmSecretAccessor.get("");
    assertNull(result);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithValidKey_NTLM() {
    String secId = "winrm-123";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secId);
      mockIdentifierRef.setAccountIdentifier("account123");
      mockIdentifierRef.setOrgIdentifier("org123");
      mockIdentifierRef.setProjectIdentifier("project123");

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response for NTLM
      SecretDTOV2 secret = SecretDTOV2.builder()
                               .type(SecretType.WinRmCredentials)
                               .name("Test WinRM NTLM Secret")
                               .identifier(secId)
                               .build();

      // Create SecretRefData for password
      SecretRefData passwordSecretRef =
          SecretRefData.builder().identifier("password-secret").scope(io.harness.encryption.Scope.ACCOUNT).build();

      NTLMConfigDTO ntlmConfigDTO = NTLMConfigDTO.builder()
                                        .domain("testdomain")
                                        .username("testuser")
                                        .password(passwordSecretRef)
                                        .useSSL(true)
                                        .useNoProfile(false)
                                        .skipCertChecks(true)
                                        .build();

      WinRmAuthDTO winRmAuthDTO = WinRmAuthDTO.builder().type(WinRmAuthScheme.NTLM).spec(ntlmConfigDTO).build();

      // Create parameter list
      List<WinRmCommandParameter> parameters = new ArrayList<>();
      parameters.add(WinRmCommandParameter.builder().parameter("timeout").value("30").build());

      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO =
          WinRmCredentialsSpecDTO.builder().port(5985).parameters(parameters).auth(winRmAuthDTO).build();

      secret.setSpec(winRmCredentialsSpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method
      Map<String, Object> result = (Map<String, Object>) winrmSecretAccessor.get(secId);

      // Verify the result
      assertThat(result).isNotNull();
      assertThat(result.get("port")).isEqualTo("5985");
      assertThat(result.get("domain")).isEqualTo("testdomain");
      assertThat(result.get("username")).isEqualTo("testuser");
      assertThat(result.get("password")).isEqualTo("${{secrets.getValue('account.password-secret')}}");
      assertThat(result.get("useSSL")).isEqualTo("true");
      assertThat(result.get("useNoProfile")).isEqualTo("false");
      assertThat(result.get("skipCertChecks")).isEqualTo("true");
      assertThat(result.get("authScheme")).isEqualTo("NTLM");
      assertThat(result.get("cmdParams")).isEqualTo(parameters.toString());
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithValidKey_Kerberos_Password() {
    String secId = "winrm-kerberos";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secId);
      mockIdentifierRef.setAccountIdentifier("account123");
      mockIdentifierRef.setOrgIdentifier("org123");
      mockIdentifierRef.setProjectIdentifier("project123");

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response for Kerberos with Password
      SecretDTOV2 secret = SecretDTOV2.builder()
                               .type(SecretType.WinRmCredentials)
                               .name("Test WinRM Kerberos Secret")
                               .identifier(secId)
                               .build();

      SecretRefData passwordSecretRef =
          SecretRefData.builder().identifier("kerberos-password").scope(io.harness.encryption.Scope.PROJECT).build();

      TGTPasswordSpecDTO tgtPasswordSpecDTO = TGTPasswordSpecDTO.builder().password(passwordSecretRef).build();

      KerberosWinRmConfigDTO kerberosConfigDTO = KerberosWinRmConfigDTO.builder()
                                                     .realm("EXAMPLE.COM")
                                                     .principal("testuser@EXAMPLE.COM")
                                                     .tgtGenerationMethod(TGTGenerationMethod.Password)
                                                     .spec(tgtPasswordSpecDTO)
                                                     .useSSL(true)
                                                     .useNoProfile(false)
                                                     .skipCertChecks(false)
                                                     .build();

      WinRmAuthDTO winRmAuthDTO = WinRmAuthDTO.builder().type(WinRmAuthScheme.Kerberos).spec(kerberosConfigDTO).build();

      List<WinRmCommandParameter> parameters = new ArrayList<>();
      parameters.add(WinRmCommandParameter.builder().parameter("timeout").value("60").build());

      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO =
          WinRmCredentialsSpecDTO.builder().port(5986).parameters(parameters).auth(winRmAuthDTO).build();

      secret.setSpec(winRmCredentialsSpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method
      Map<String, Object> result = (Map<String, Object>) winrmSecretAccessor.get(secId);

      // Verify the result
      assertThat(result).isNotNull();
      assertThat(result.get("port")).isEqualTo("5986");
      assertThat(result.get("domain")).isEqualTo("EXAMPLE.COM");
      assertThat(result.get("username")).isEqualTo("testuser@EXAMPLE.COM");
      assertThat(result.get("password")).isEqualTo("${{secrets.getValue('kerberos-password')}}");
      assertThat(result.get("useSSL")).isEqualTo("true");
      assertThat(result.get("useNoProfile")).isEqualTo("false");
      assertThat(result.get("skipCertChecks")).isEqualTo("false");
      assertThat(result.get("authScheme")).isEqualTo("KERBEROS");
      assertThat(result.get("isUseKeyTab")).isEqualTo("false");
      assertThat(result.get("cmdParams")).isEqualTo(parameters.toString());
      assertThat(result.get("id")).isEqualTo(secId);
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithValidKey_Kerberos_KeyTab() {
    String secId = "winrm-keytab";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secId);
      mockIdentifierRef.setAccountIdentifier("account123");
      mockIdentifierRef.setOrgIdentifier("org123");
      mockIdentifierRef.setProjectIdentifier("project123");

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response for Kerberos with KeyTab
      SecretDTOV2 secret = SecretDTOV2.builder()
                               .type(SecretType.WinRmCredentials)
                               .name("Test WinRM Kerberos KeyTab Secret")
                               .identifier(secId)
                               .build();

      TGTKeyTabFilePathSpecDTO tgtKeyTabSpecDTO =
          TGTKeyTabFilePathSpecDTO.builder().keyPath("/path/to/service.keytab").build();

      KerberosWinRmConfigDTO kerberosConfigDTO = KerberosWinRmConfigDTO.builder()
                                                     .realm("EXAMPLE.COM")
                                                     .principal("service@EXAMPLE.COM")
                                                     .tgtGenerationMethod(TGTGenerationMethod.KeyTabFilePath)
                                                     .spec(tgtKeyTabSpecDTO)
                                                     .useSSL(false)
                                                     .useNoProfile(true)
                                                     .skipCertChecks(true)
                                                     .build();

      WinRmAuthDTO winRmAuthDTO = WinRmAuthDTO.builder().type(WinRmAuthScheme.Kerberos).spec(kerberosConfigDTO).build();

      List<WinRmCommandParameter> parameters = new ArrayList<>();
      parameters.add(WinRmCommandParameter.builder().parameter("timeout").value("45").build());

      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO =
          WinRmCredentialsSpecDTO.builder().port(5986).parameters(parameters).auth(winRmAuthDTO).build();

      secret.setSpec(winRmCredentialsSpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method
      Map<String, Object> result = (Map<String, Object>) winrmSecretAccessor.get(secId);

      // Verify the result
      assertThat(result).isNotNull();
      assertThat(result.get("port")).isEqualTo("5986");
      assertThat(result.get("domain")).isEqualTo("EXAMPLE.COM");
      assertThat(result.get("username")).isEqualTo("service@EXAMPLE.COM");
      assertThat(result.get("useSSL")).isEqualTo("false");
      assertThat(result.get("useNoProfile")).isEqualTo("true");
      assertThat(result.get("skipCertChecks")).isEqualTo("true");
      assertThat(result.get("authScheme")).isEqualTo("KERBEROS");
      assertThat(result.get("isUseKeyTab")).isEqualTo("true");
      assertThat(result.get("keyTabFilePath")).isEqualTo("/path/to/service.keytab");
      assertThat(result.get("cmdParams")).isEqualTo(parameters.toString());
      assertThat(result.get("id")).isEqualTo(secId);
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithInvalidAuthScheme() {
    String secId = "winrm-123";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      mockIdentifierRef.setIdentifier(secId);

      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      // Create mock secret response with invalid auth scheme
      SecretDTOV2 secret = SecretDTOV2.builder()
                               .type(SecretType.WinRmCredentials)
                               .name("Test WinRM Invalid Secret")
                               .identifier(secId)
                               .build();

      WinRmAuthDTO winRmAuthDTO = WinRmAuthDTO.builder()
                                      .type(null) // Null auth scheme will trigger the default case
                                      .spec(null)
                                      .build();

      List<WinRmCommandParameter> emptyParameters = new ArrayList<>();
      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO =
          WinRmCredentialsSpecDTO.builder().port(5985).parameters(emptyParameters).auth(winRmAuthDTO).build();

      secret.setSpec(winRmCredentialsSpecDTO);
      SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder()
                                                        .secret(secret)
                                                        .createdAt(System.currentTimeMillis())
                                                        .updatedAt(System.currentTimeMillis())
                                                        .draft(false)
                                                        .build();

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString())).thenReturn(secretResponseWrapper);

      // Test the get method should throw exception due to invalid auth scheme
      assertThatThrownBy(() -> winrmSecretAccessor.get(secId))
          .isInstanceOf(EngineFunctorException.class)
          .hasMessageContaining("Failed to retrieve WinRM secret");
    }
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGet_WithSecretError() {
    String secId = "winrm-123";

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class);
         MockedStatic<IdentifierRefHelper> identifierRefHelperMock = Mockito.mockStatic(IdentifierRefHelper.class);
         MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      // Setup mocks
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn("account123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn("org123");
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn("project123");

      IdentifierRef mockIdentifierRef = new IdentifierRef();
      identifierRefHelperMock
          .when(() -> IdentifierRefHelper.getIdentifierRef(secId, "account123", "org123", "project123"))
          .thenReturn(mockIdentifierRef);

      when(secretNGManagerClient.getSecret(anyString(), anyString(), anyString(), anyString())).thenReturn(secretCall);

      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any(), anyString()))
          .thenThrow(new RuntimeException("Secret not found"));

      // Test the get method
      assertThatThrownBy(() -> winrmSecretAccessor.get(secId))
          .isInstanceOf(EngineFunctorException.class)
          .hasMessageContaining("Failed to retrieve WinRM secret");
    }
  }
}