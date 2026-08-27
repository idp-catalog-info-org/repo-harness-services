/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.exception.EngineFunctorException;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.secrets.remote.SecretNGManagerClient;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class WinrmSecretAccessor extends BaseSecretAccessor {
  public WinrmSecretAccessor(Ambiance ambiance, SecretNGManagerClient secretNGManagerClient) {
    super(ambiance, secretNGManagerClient);
  }

  @Override
  protected Map<String, Object> getSecretByIdentifier(String secretIdentifier) {
    try {
      IdentifierRef identifierRef = createIdentifierRef(secretIdentifier);
      SecretSpecDTO secretSpecDTO = getSecretSpec(identifierRef, secretIdentifier);

      // Get the Winrm key spec from the secret
      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO = (WinRmCredentialsSpecDTO) secretSpecDTO;
      if (winRmCredentialsSpecDTO == null) {
        throw new EngineFunctorException(String.format("Winrm secret [%s] has no spec data", secretIdentifier));
      }

      WinRmAuthDTO winRmAuthDTO = winRmCredentialsSpecDTO.getAuth();
      SecretInputDTO secretInputDTO;

      switch (winRmAuthDTO.getAuthScheme()) {
        case NTLM:
          NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) winRmAuthDTO.getSpec();
          secretInputDTO = toNTLMSecretsInputDTO(identifierRef, winRmCredentialsSpecDTO, ntlmConfigDTO);
          break;
        case Kerberos:
          KerberosWinRmConfigDTO kerberosWinRmConfigDTO = (KerberosWinRmConfigDTO) winRmAuthDTO.getSpec();
          secretInputDTO = toKerberosSecretsInputDTO(identifierRef, winRmCredentialsSpecDTO, kerberosWinRmConfigDTO);
          break;
        default:
          throw new IllegalArgumentException("Invalid authScheme provided:" + winRmAuthDTO.getAuthScheme());
      }

      return buildSecretFieldMap(secretInputDTO);

    } catch (Exception ex) {
      handleSecretRetrievalException(secretIdentifier, "WinRM", ex);
      return null; // This line will never be reached due to exception throwing
    }
  }

  private SecretInputDTO toNTLMSecretsInputDTO(
      IdentifierRef identifierRef, WinRmCredentialsSpecDTO winRmCredentialsSpecDTO, NTLMConfigDTO ntlmConfigDTO) {
    return WinrmSecretInputDTO.builder()
        .id(identifierRef.getIdentifier())
        .scope(Scope.of(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier()))
        .port(String.valueOf(winRmCredentialsSpecDTO.getPort()))
        .authScheme(AuthenticationScheme.NTLM.name())
        .domain(ntlmConfigDTO.getDomain())
        .username(ntlmConfigDTO.getUsername())
        .useSSL(String.valueOf(ntlmConfigDTO.isUseSSL()))
        .useNoProfile(String.valueOf(ntlmConfigDTO.isUseNoProfile()))
        .skipCertChecks(String.valueOf(ntlmConfigDTO.isSkipCertChecks()))
        .cmdParams(winRmCredentialsSpecDTO.getParameters().toString())
        .password(convertSecretRefDataToString(ntlmConfigDTO.getPassword()))
        .build();
  }

  private SecretInputDTO toKerberosSecretsInputDTO(IdentifierRef identifierRef,
      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO, KerberosWinRmConfigDTO kerberosWinRmConfigDTO) {
    boolean isUseKeyTab = false;
    String password = "";
    String keyTabFilePath = "";

    if (kerberosWinRmConfigDTO.getTgtGenerationMethod() != null) {
      switch (kerberosWinRmConfigDTO.getTgtGenerationMethod()) {
        case Password:
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosWinRmConfigDTO.getSpec();
          password = convertSecretRefDataToString(tgtPasswordSpecDTO.getPassword());
          break;

        case KeyTabFilePath:
          TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO =
              (TGTKeyTabFilePathSpecDTO) kerberosWinRmConfigDTO.getSpec();
          isUseKeyTab = true;
          keyTabFilePath = tgtKeyTabFilePathSpecDTO.getKeyPath();
          break;

        default:
          throw new IllegalArgumentException(
              "Invalid TgtGenerationMethod provided:" + kerberosWinRmConfigDTO.getTgtGenerationMethod());
      }
    }

    return WinrmSecretInputDTO.builder()
        .id(identifierRef.getIdentifier())
        .scope(Scope.of(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier()))
        .port(String.valueOf(winRmCredentialsSpecDTO.getPort()))
        .authScheme(AuthenticationScheme.KERBEROS.name())
        .domain(kerberosWinRmConfigDTO.getRealm())
        .username(kerberosWinRmConfigDTO.getPrincipal())
        .useSSL(String.valueOf(kerberosWinRmConfigDTO.isUseSSL()))
        .useNoProfile(String.valueOf(kerberosWinRmConfigDTO.isUseNoProfile()))
        .skipCertChecks(String.valueOf(kerberosWinRmConfigDTO.isSkipCertChecks()))
        .cmdParams(winRmCredentialsSpecDTO.getParameters().toString())
        .password(password)
        .isUseKeyTab(String.valueOf(isUseKeyTab))
        .keyTabFilePath(keyTabFilePath)
        .build();
  }
}