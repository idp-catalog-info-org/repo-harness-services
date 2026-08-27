/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class SecretVariableConstants {
  // SSH connection details
  public static final String PLUGIN_SSH_USERNAME = "PLUGIN_SSH_USERNAME";
  public static final String PLUGIN_SSH_PASSWORD = "PLUGIN_SSH_PASSWORD";
  public static final String PLUGIN_SSH_KEY = "PLUGIN_SSH_KEY";
  public static final String PLUGIN_SSH_KEY_PASSPHRASE = "PLUGIN_SSH_KEY_PASSPHRASE";
  public static final String PLUGIN_SSH_KEY_PATH = "PLUGIN_SSH_KEY_PATH";
  public static final String PLUGIN_SSH_PORT = "PLUGIN_SSH_PORT";

  // SSH Kerberos authentication
  public static final String PLUGIN_SSH_KERBEROS_PRINCIPAL = "PLUGIN_SSH_KERBEROS_PRINCIPAL";
  public static final String PLUGIN_SSH_KERBEROS_REALM = "PLUGIN_SSH_KERBEROS_REALM";
  public static final String PLUGIN_SSH_KERBEROS_PASSWORD = "PLUGIN_SSH_KERBEROS_PASSWORD";
  public static final String PLUGIN_SSH_KERBEROS_KEYTAB_PATH = "PLUGIN_SSH_KERBEROS_KEYTAB_PATH";

  // WinRM connection details.
  public static final String PLUGIN_WINRM_USERNAME = "PLUGIN_WINRM_USERNAME";
  public static final String PLUGIN_WINRM_PASSWORD = "PLUGIN_WINRM_PASSWORD";
  public static final String PLUGIN_WINRM_DOMAIN = "PLUGIN_WINRM_DOMAIN";
  public static final String PLUGIN_WINRM_PORT = "PLUGIN_WINRM_PORT";

  // WinRM Kerberos authentication.
  public static final String PLUGIN_WINRM_KERBEROS_PRINCIPAL = "PLUGIN_WINRM_KERBEROS_PRINCIPAL";
  public static final String PLUGIN_WINRM_KERBEROS_REALM = "PLUGIN_WINRM_KERBEROS_REALM";
  public static final String PLUGIN_WINRM_KERBEROS_PASSWORD = "PLUGIN_WINRM_KERBEROS_PASSWORD";
  public static final String PLUGIN_WINRM_KERBEROS_KEYTAB_PATH = "PLUGIN_WINRM_KERBEROS_KEYTAB_PATH";

  // Connection settings.
  public static final String PLUGIN_WINRM_USE_SSL = "PLUGIN_WINRM_USE_SSL";
  public static final String PLUGIN_WINRM_SKIP_CERT_CHECK = "PLUGIN_WINRM_SKIP_CERT_CHECK";
  public static final String PLUGIN_WINRM_USE_NO_PROFILE = "PLUGIN_WINRM_USE_NO_PROFILE";

  // Command parameters.
  public static final String PLUGIN_WINRM_CMD_PARAMS = "PLUGIN_WINRM_CMD_PARAMS";
}
