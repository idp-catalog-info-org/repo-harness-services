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
import io.harness.exception.EngineFunctorException;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.models.TGTPasswordSpec;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.MapperUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class SSHSecretAccessor extends BaseSecretAccessor {
  private static final String KEY_PATH = "keyPath";

  public SSHSecretAccessor(Ambiance ambiance, SecretNGManagerClient secretNGManagerClient) {
    super(ambiance, secretNGManagerClient);
  }

  @Override
  protected Map<String, Object> getSecretByIdentifier(String secretIdentifier) {
    try {
      IdentifierRef identifierRef = createIdentifierRef(secretIdentifier);
      SecretSpecDTO secretSpecDTO = getSecretSpec(identifierRef, secretIdentifier);

      // Get the SSH key spec from the secret
      SSHKeySpecDTO sshKeySpecDTO = (SSHKeySpecDTO) secretSpecDTO;
      if (sshKeySpecDTO == null || sshKeySpecDTO.getAuth() == null || sshKeySpecDTO.getAuth().getSpec() == null) {
        throw new EngineFunctorException(String.format("SSH secret [%s] has no spec data", secretIdentifier));
      }

      if (sshKeySpecDTO.getAuth().getSpec() instanceof SSHConfigDTO) {
        SSHConfigDTO sshConfig = (SSHConfigDTO) sshKeySpecDTO.getAuth().getSpec();
        SecretInputDTO secretInputDTO = toSecretsInputDTO(identifierRef, sshKeySpecDTO, sshConfig);
        return buildSecretFieldMap(secretInputDTO);
      } else if (sshKeySpecDTO.getAuth().getSpec() instanceof KerberosConfigDTO) {
        KerberosConfigDTO kerberosConfig = (KerberosConfigDTO) sshKeySpecDTO.getAuth().getSpec();
        SecretInputDTO secretInputDTO = toKerberosSecretsInputDTO(identifierRef, sshKeySpecDTO, kerberosConfig);
        return buildSecretFieldMap(secretInputDTO);
      }

      log.warn("Unexpected secret type encountered for identifier: {}", secretIdentifier);
      return new HashMap<>();

    } catch (Exception ex) {
      handleSecretRetrievalException(secretIdentifier, "SSH", ex);
      return null; // This line will never be reached due to exception throwing
    }
  }

  private SecretInputDTO toKerberosSecretsInputDTO(
      IdentifierRef identifierRef, SSHKeySpecDTO sshKeySpecDTO, KerberosConfigDTO kerberosConfigDTO) {
    String principal = "";
    String realm = "";
    String password = "";
    String keyTabFilePath = "";

    if (kerberosConfigDTO.getPrincipal() != null) {
      principal = kerberosConfigDTO.getPrincipal();
    }

    if (kerberosConfigDTO.getRealm() != null) {
      realm = kerberosConfigDTO.getRealm();
    }

    if (kerberosConfigDTO.getSpec() != null) {
      Map<String, Object> specMap = MapperUtils.toMapViaJsonString(kerberosConfigDTO.getSpec());

      if (specMap.containsKey(KEY_PATH)) {
        keyTabFilePath = specMap.get(KEY_PATH).toString();
      }

      Object entity = kerberosConfigDTO.getSpec().toEntity();
      if (entity instanceof TGTPasswordSpec) {
        Object dto = ((TGTPasswordSpec) entity).toDTO();
        if (dto instanceof TGTPasswordSpecDTO) {
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) dto;
          if (tgtPasswordSpecDTO.getPassword() != null) {
            password = convertSecretRefDataToString(tgtPasswordSpecDTO.getPassword());
          }
        }
      }
    }
    return SSHKerberosSecretInputDTO.builder()
        .id(String.valueOf(identifierRef))
        .scope(Scope.of(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier()))
        .principal(principal)
        .realm(realm)
        .password(password)
        .keyTabFilePath(keyTabFilePath)
        .port(String.valueOf(sshKeySpecDTO.getPort()))
        .spec(MapperUtils.toMapViaJsonString(kerberosConfigDTO.getSpec()).toString())
        .build();
  }

  private SecretInputDTO toSecretsInputDTO(
      IdentifierRef identifierRef, SSHKeySpecDTO sshKeySpecDTO, SSHConfigDTO sshConfig) {
    String username = "";
    String password = "";
    String keyPath = "";
    String passphrase = "";
    String key = "";

    // Extract fields from spec based on credential type
    if (sshConfig.getSpec() != null) {
      Map<String, Object> specMap = MapperUtils.toMapViaJsonString(sshConfig.getSpec());

      if (specMap.containsKey("userName")) {
        username = specMap.get("userName").toString();
      }

      if (specMap.containsKey(KEY_PATH)) {
        keyPath = specMap.get(KEY_PATH).toString();
      }

      switch (sshConfig.getCredentialType()) {
        case Password:
          SSHPasswordCredentialDTO passwordCredential = (SSHPasswordCredentialDTO) sshConfig.getSpec();
          if (passwordCredential.getPassword() != null) {
            password = convertSecretRefDataToString(passwordCredential.getPassword());
          }
          break;
        case KeyReference:
          SSHKeyReferenceCredentialDTO keyRefCredential = (SSHKeyReferenceCredentialDTO) sshConfig.getSpec();
          if (keyRefCredential.getKey() != null) {
            key = convertSecretRefDataToString(keyRefCredential.getKey());
          }
          if (keyRefCredential.getEncryptedPassphrase() != null) {
            passphrase = convertSecretRefDataToString(keyRefCredential.getEncryptedPassphrase());
          }
          break;
        case KeyPath:
          SSHKeyPathCredentialDTO keyPathCredential = (SSHKeyPathCredentialDTO) sshConfig.getSpec();
          if (keyPathCredential.getEncryptedPassphrase() != null) {
            passphrase = convertSecretRefDataToString(keyPathCredential.getEncryptedPassphrase());
          }
          break;
        default:
          break;
      }
    }
    return SSHSecretInputDTO.builder()
        .id(String.valueOf(identifierRef))
        .scope(Scope.of(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier()))
        .authScheme(sshKeySpecDTO.getAuth().getAuthScheme().toString())
        .port(String.valueOf(sshKeySpecDTO.getPort()))
        .credType(sshConfig.getCredentialType().toString())
        .username(username)
        .password(password)
        .keyPath(keyPath)
        .key(key)
        .passphrase(passphrase)
        .spec(MapperUtils.toMapViaJsonString(sshConfig.getSpec()).toString())
        .build();
  }
}