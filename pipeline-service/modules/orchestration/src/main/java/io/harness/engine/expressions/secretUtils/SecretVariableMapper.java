/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class SecretVariableMapper {
  public Map<String, String> toSecretVariables(SecretSpecDTO secretSpecDTO, Map<String, Object> secretFields) {
    if (secretSpecDTO instanceof SSHKeySpecDTO) {
      return toSshSecretVariables(secretFields);
    } else if (secretSpecDTO instanceof WinRmCredentialsSpecDTO) {
      return toWinrmSecretVariables(secretFields);
    }
    throw new InvalidRequestException("Invalid secret input type.");
  }

  public static Map<String, String> toSshSecretVariables(Map<String, Object> secretFields) {
    Map<String, String> variables = new HashMap<>();
    // SSH Authentication includes: Password, Key and KeyPath
    variables.put(SecretVariableConstants.PLUGIN_SSH_USERNAME, getString(secretFields.get("username")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_PASSWORD, getString(secretFields.get("password")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_KEY, getString(secretFields.get("key")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_KEY_PASSPHRASE, getString(secretFields.get("passphrase")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_KEY_PATH, getString(secretFields.get("keyPath")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_PORT, getString(secretFields.get("port")));

    // Kerberos Authentication includes: Principal, Password, Realm and KeytabPath
    variables.put(SecretVariableConstants.PLUGIN_SSH_KERBEROS_PRINCIPAL, getString(secretFields.get("principal")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_KERBEROS_PASSWORD, getString(secretFields.get("password")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_KERBEROS_REALM, getString(secretFields.get("realm")));
    variables.put(SecretVariableConstants.PLUGIN_SSH_KERBEROS_KEYTAB_PATH, getString(secretFields.get("keyPath")));
    return variables;
  }

  public static Map<String, String> toWinrmSecretVariables(Map<String, Object> secretFields) {
    Map<String, String> variables = new HashMap<>();
    // WinRM NTLM Authentication includes: Password, Domain and Port
    variables.put(SecretVariableConstants.PLUGIN_WINRM_USERNAME, getString(secretFields.get("username")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_PASSWORD, getString(secretFields.get("password")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_DOMAIN, getString(secretFields.get("domain")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_PORT, getString(secretFields.get("port")));

    // Kerberos Authentication includes: Principal, Password, Realm and KeytabPath
    variables.put(SecretVariableConstants.PLUGIN_WINRM_KERBEROS_PRINCIPAL, getString(secretFields.get("principal")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_KERBEROS_PASSWORD, getString(secretFields.get("password")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_KERBEROS_REALM, getString(secretFields.get("realm")));
    variables.put(
        SecretVariableConstants.PLUGIN_WINRM_KERBEROS_KEYTAB_PATH, getString(secretFields.get("keyTabFilePath")));

    // WinRM connection details.
    variables.put(SecretVariableConstants.PLUGIN_WINRM_USE_SSL, getString(secretFields.get("useSSL")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_USE_NO_PROFILE, getString(secretFields.get("useNoProfile")));
    variables.put(SecretVariableConstants.PLUGIN_WINRM_SKIP_CERT_CHECK, getString(secretFields.get("skipCertChecks")));

    variables.put(SecretVariableConstants.PLUGIN_WINRM_CMD_PARAMS, getString(secretFields.get("cmdParams")));
    return variables;
  }

  private static String getString(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
